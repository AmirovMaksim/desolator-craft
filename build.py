import subprocess, os, re, shutil

ROOT = "D:/projects/minecraftclonebydesolatore"
M2 = os.path.join(os.environ["USERPROFILE"], ".m2", "repository")

# collect jme-compatible jars (LWJGL 3.3.2 only)
jars = []
for r, _, fs in os.walk(M2):
    for f in fs:
        if f.endswith(".jar"):
            jars.append(os.path.join(r, f))

def keep(p):
    low = p.lower().replace("\\", "/")
    if "/org/lwjgl/" in low:
        m = re.search(r"lwjgl-?[a-z]*-?([0-9]+\.[0-9]+\.[0-9]+)", low)
        if m and m.group(1) != "3.3.2":
            return False
    # skip native jars (JME loads .dll itself)
    if low.endswith("-natives.jar") or "-natives-" in low:
        return False
    return True

cp = [j for j in jars if keep(j)]
src = os.path.join(ROOT, "src", "main", "java")
resources = os.path.join(ROOT, "src", "main", "resources")
out = os.path.join(ROOT, "target", "classes")

jf = [os.path.join(r, f) for r, _, fs in os.walk(src) for f in fs if f.endswith(".java")]

r = subprocess.run(["javac", "--release", "17", "-cp", ";".join(cp), "-d", out] + jf,
                    capture_output=True, text=True, encoding="utf-8", errors="replace")
print("COMPILE EXIT:", r.returncode)
if r.returncode != 0:
    print(r.stderr[:2000])
    raise SystemExit(1)

# copy resources (sprites) into target/classes so they are on classpath
if os.path.isdir(resources):
    for r2, _, fs in os.walk(resources):
        for f in fs:
            srcf = os.path.join(r2, f)
            rel = os.path.relpath(srcf, resources)
            dst = os.path.join(out, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(srcf, dst)
    print("resources copied to target/classes")
else:
    print("no resources dir")

nclasses = sum(1 for _, _, fs in os.walk(out) for x in fs if x.endswith(".class"))
print("classes:", nclasses)
