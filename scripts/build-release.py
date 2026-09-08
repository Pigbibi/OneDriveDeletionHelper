#!/usr/bin/env python3
"""Build a signed APK with a private local key, without putting secrets in command arguments or logs."""
import argparse
import hashlib
import os
from pathlib import Path
import secrets
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--init-key", action="store_true", help="Create a new private signing key if none exists")
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
private = Path(os.environ.get("PHOTOKEEP_SIGNING_DIR", str(Path.home() / ".local/share/photokeep/signing"))).resolve()
if private == root or root in private.parents:
    raise SystemExit("Signing material must be stored outside the project.")
private.mkdir(parents=True, exist_ok=True, mode=0o700)
private.chmod(0o700)
key = private / "release.p12"
password_file = private / "store-password"
env = os.environ.copy()
if not key.exists():
    if not args.init_key:
        raise SystemExit("No release key. First publisher: use --init-key. Existing publisher: restore the original key.")
    if password_file.exists():
        raise SystemExit("Incomplete signing state; recover the original key rather than replacing it.")
    password = secrets.token_urlsafe(48)
    with password_file.open("x") as output:
        os.chmod(password_file, 0o600)
        output.write(password)
    env["PHOTOKEEP_STORE_PASSWORD"] = password
    java_home = env.get("JAVA_HOME")
    keytool = str(Path(java_home) / "bin/keytool") if java_home else "keytool"
    result = subprocess.run([keytool, "-genkeypair", "-keystore", str(key), "-storetype", "PKCS12",
                             "-storepass:env", "PHOTOKEEP_STORE_PASSWORD", "-keypass:env", "PHOTOKEEP_STORE_PASSWORD",
                             "-alias", "photokeep", "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000",
                             "-dname", "CN=Pigbibi, OU=PhotoKeep, O=Pigbibi", "-noprompt"],
                            env=env, capture_output=True, text=True)
    if result.returncode:
        raise SystemExit("Signing key creation failed; no credential output was printed.")
    key.chmod(0o600)
if not password_file.exists():
    raise SystemExit("The signing password is missing; restore it from private storage.")
env["PHOTOKEEP_STORE_PASSWORD"] = password_file.read_text().strip()
env["PHOTOKEEP_KEYSTORE"] = str(key)
subprocess.run([str(root / "gradlew"), ":app:testDebugUnitTest", ":app:lintRelease", ":app:assembleRelease", "--console=plain"], cwd=root, env=env, check=True)
artifact = root / "dist/PhotoKeep-0.1.0.apk"
artifact.parent.mkdir(exist_ok=True)
shutil.copyfile(root / "app/build/outputs/apk/release/app-release.apk", artifact)
digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
(root / "dist/SHA256SUMS.txt").write_text(f"{digest}  {artifact.name}\n")
print(f"Built {artifact.name}; signing material remains outside the project.")
