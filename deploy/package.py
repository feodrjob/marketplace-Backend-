#!/usr/bin/env python3
"""Build a self-contained deployment bundle from the project's Compose file."""

import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess


def run(*args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def runtime_config(config, sha):
    """Preserve runtime interpolation, but require only bundled local images."""
    config = json.loads(json.dumps(config))
    config.pop("name", None)
    for name, service in config["services"].items():
        service.pop("build", None)
        service["image"] = f"marketplace-{name}:{sha}"
        service["pull_policy"] = "never"
    return config


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("sha")
    parser.add_argument("platform", choices=["linux/amd64", "linux/arm64"])
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.sha):
        parser.error("Expected a full commit SHA")
    root = Path(__file__).resolve().parent.parent
    os.chdir(root)
    args.output = args.output.resolve()
    args.output.mkdir(parents=True, exist_ok=False)
    # Do not read the developer's .env or resolve production secrets on CI.
    config = json.loads(run(
        "docker", "compose", "--env-file", "/dev/null", "-f", "compose.yaml",
        "config", "--no-interpolate", "--no-normalize", "--format", "json",
        capture_output=True, text=True,
    ).stdout)
    images = []
    for name, service in config["services"].items():
        tag = f"marketplace-{name}:{args.sha}"
        build = service.get("build")
        if build:
            # Build each Compose service for the architecture reported by the VM.
            command = ["docker", "buildx", "build", "--load", "--platform", args.platform,
                       "--tag", tag, "--file", str(Path(build["context"]) / build.get("dockerfile", "Dockerfile"))]
            for key, value in build.get("args", {}).items():
                command.extend(["--build-arg", f"{key}={value}"])
            command.append(build["context"])
            run(*command)
        else:
            run("docker", "pull", "--platform", args.platform, service["image"])
            run("docker", "tag", service["image"], tag)
        images.append(tag)
    (args.output / "compose.json").write_text(
        json.dumps(runtime_config(config, args.sha), indent=2) + "\n"
    )
    (args.output / "platform").write_text(args.platform + "\n")
    shutil.copyfile(root / "deploy" / "install.sh", args.output / "install.sh")
    # Save all services, including upstream images such as PostgreSQL.
    with (args.output / "images.tar.gz").open("wb") as output:
        with gzip.GzipFile(fileobj=output, mode="wb", compresslevel=1) as compressed:
            process = subprocess.Popen(["docker", "image", "save", *images], stdout=subprocess.PIPE)
            try:
                shutil.copyfileobj(process.stdout, compressed)
            finally:
                process.stdout.close()
            if process.wait() != 0:
                raise RuntimeError("docker image save failed")
    with (args.output / "SHA256SUMS").open("w") as checksums:
        for name in ("compose.json", "platform", "install.sh", "images.tar.gz"):
            digest = hashlib.sha256()
            with (args.output / name).open("rb") as file:
                for chunk in iter(lambda: file.read(1024 * 1024), b""):
                    digest.update(chunk)
            checksums.write(f"{digest.hexdigest()}  {name}\n")


if __name__ == "__main__":
    main()
