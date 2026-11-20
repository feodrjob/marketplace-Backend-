#!/usr/bin/env python3
"""Publish Compose images and prepare a small, digest-pinned release bundle."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


def run(*args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def runtime_config(config, images):
    """Keep VM-side environment substitution and use published immutable images."""
    config = json.loads(json.dumps(config))
    config.pop("name", None)
    for name, service in config["services"].items():
        service.pop("build", None)
        service["image"] = images[name]
        service["pull_policy"] = "missing"
    return config


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("sha")
    parser.add_argument("platform", choices=["linux/amd64", "linux/arm64"])
    parser.add_argument("registry")
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.sha):
        parser.error("Expected a full commit SHA")
    if not re.fullmatch(r"[a-z0-9][a-z0-9.-]*\.cr\.cloud\.ru", args.registry):
        parser.error("Expected a Cloud.ru registry hostname")
    root = Path(__file__).resolve().parent.parent
    os.chdir(root)
    args.output = args.output.resolve()
    args.output.mkdir(parents=True, exist_ok=False)
    # Never read a developer's .env or resolve production secrets on CI.
    config = json.loads(run(
        "docker", "compose", "--env-file", "/dev/null", "-f", "compose.yaml",
        "config", "--no-interpolate", "--no-normalize", "--format", "json",
        capture_output=True, text=True,
    ).stdout)
    images = {}
    for name, service in config["services"].items():
        repository = f"{args.registry}/{name}"
        tag = f"{repository}:{args.sha}"
        build = service.get("build")
        if build:
            with tempfile.TemporaryDirectory() as directory:
                metadata = Path(directory) / "metadata.json"
                command = ["docker", "buildx", "build", "--push", "--platform", args.platform,
                           "--tag", tag, "--metadata-file", str(metadata),
                           "--file", str(Path(build["context"]) / build.get("dockerfile", "Dockerfile"))]
                for key, value in build.get("args", {}).items():
                    command.extend(["--build-arg", f"{key}={value}"])
                command.append(build["context"])
                run(*command)
                digest = json.loads(metadata.read_text())["containerimage.digest"]
        else:
            # Mirror dependencies too: the VM only needs access to our registry.
            run("docker", "pull", "--platform", args.platform, service["image"])
            run("docker", "tag", service["image"], tag)
            run("docker", "push", tag)
            references = json.loads(run(
                "docker", "image", "inspect", tag, "--format", "{{json .RepoDigests}}",
                capture_output=True, text=True,
            ).stdout)
            digest = next(ref.split("@", 1)[1] for ref in references
                          if ref.startswith(f"{repository}@"))
        if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
            raise ValueError(f"Invalid published digest for {name}: {digest}")
        images[name] = f"{repository}@{digest}"
    (args.output / "compose.json").write_text(
        json.dumps(runtime_config(config, images), indent=2) + "\n"
    )
    (args.output / "platform").write_text(args.platform + "\n")
    shutil.copyfile(root / "deploy" / "install.sh", args.output / "install.sh")
    with (args.output / "SHA256SUMS").open("w") as checksums:
        for name in ("compose.json", "platform", "install.sh"):
            digest = hashlib.sha256((args.output / name).read_bytes()).hexdigest()
            checksums.write(f"{digest}  {name}\n")


if __name__ == "__main__":
    main()
