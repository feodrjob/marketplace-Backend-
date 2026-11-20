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


def published_image(repository, tag):
    """Hash the target registry's manifest, not the local source image/index."""
    manifest = run(
        "docker", "buildx", "imagetools", "inspect", "--raw", tag,
        capture_output=True,
    ).stdout
    if json.loads(manifest).get("schemaVersion") != 2:
        raise ValueError(f"Unsupported registry manifest for {tag}")
    digest = "sha256:" + hashlib.sha256(manifest).hexdigest()
    reference = f"{repository}@{digest}"
    # Check the exact reference the VM will pull before shipping the release.
    pinned_manifest = run(
        "docker", "buildx", "imagetools", "inspect", "--raw", reference,
        capture_output=True,
    ).stdout
    if pinned_manifest != manifest:
        raise ValueError(f"Registry manifest mismatch for {reference}")
    print(f"Verified published image: {reference}", flush=True)
    return reference


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
            command = ["docker", "buildx", "build", "--push", "--platform", args.platform,
                       "--tag", tag,
                       "--file", str(Path(build["context"]) / build.get("dockerfile", "Dockerfile"))]
            for key, value in build.get("args", {}).items():
                command.extend(["--build-arg", f"{key}={value}"])
            command.append(build["context"])
            run(*command)
        else:
            # Mirror dependencies too: the VM only needs access to our registry.
            run("docker", "pull", "--platform", args.platform, service["image"])
            run("docker", "tag", service["image"], tag)
            run("docker", "push", tag)
        images[name] = published_image(repository, tag)
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
