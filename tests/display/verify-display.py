"""Build and check the packaged game without touching player saves (Python 3, JDK 17+)."""
from pathlib import Path
import argparse
import json
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
DISPLAY = [
    "screen.PlayInputChecks", "screen.EscapeResponseChecks", "screen.MenuResponseChecks",
    "screen.NavigationChecks", "screen.GameplaySizeChecks", "screen.DisplaySizeChecks",
    "screen.WindowPolishChecks",
]
REGRESSION = [
    "CampaignMovementTest", "TwoHitEnemyTest", "FinalBossAttackTest", "EffectIntegrationTest",
    "engine.EntryGateChecks", "engine.RunTest", "engine.EndlessUiChecks",
    "engine.ConnectedSystemsChecks", "engine.ShopEffectsChecks", "engine.RewardItemsTest",
    "engine.PatternChecks", "screen.StartupAnimationTest",
]
NATIVE = ["engine.FocusInputChecks", "engine.NativeWindowChecks", "engine.DisplayChecks"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--full", action="store_true", help="also run existing gameplay regression checks")
    parser.add_argument("--native", action="store_true", help="also open disposable native test windows")
    parser.add_argument("--output", type=Path, help="directory for logs, screenshots and results")
    parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"), help="JDK directory (defaults to JAVA_HOME)")
    parser.add_argument("--suite", action="append", choices=DISPLAY + REGRESSION + NATIVE, help="run only the named suite (repeatable)")
    args = parser.parse_args()
    suffix = ".exe" if os.name == "nt" else ""
    jdk_bin = Path(args.java_home) / "bin" if args.java_home else None
    javac = str(jdk_bin / ("javac" + suffix)) if jdk_bin else shutil.which("javac")
    if not javac:
        raise SystemExit("JDK 17+ required: put javac on PATH.")
    java = str(jdk_bin / ("java" + suffix)) if jdk_bin else shutil.which("java")
    jar = str(jdk_bin / ("jar" + suffix)) if jdk_bin else (shutil.which("jar") or str(Path(javac).resolve().with_name("jar" + suffix)))
    if not java or not Path(jar).is_file():
        raise SystemExit("Put java and jar from the same JDK on PATH.")
    output = (args.output or Path(tempfile.mkdtemp(prefix="kfc-display-results-"))).resolve()
    output.mkdir(parents=True, exist_ok=True)
    results = []
    with tempfile.TemporaryDirectory(prefix="kfc-display-checks-") as temporary:
        runtime = Path(temporary)
        production, all_classes, fixtures = [runtime / name for name in ("production", "classes", "fixtures")]
        sources = sorted((ROOT / "src").rglob("*.java"))
        tests = sorted((ROOT / "tests/display").glob("*.java"))
        tests += [ROOT / "tests/DisplayChecks.java"]
        if args.full or any(suite in REGRESSION for suite in (args.suite or [])):
            tests += sorted((ROOT / "tests/endless").glob("*.java"))
            tests += sorted((ROOT / "tests/startup").glob("*.java"))
            tests += [ROOT / "tests" / (name + ".java") for name in REGRESSION[:4]]
        for name, destination, files in (("production", production, sources), ("tests", all_classes, sources + tests)):
            destination.mkdir()
            arguments = runtime / (name + ".args")
            arguments.write_text("\n".join('"' + p.as_posix() + '"' for p in files), encoding="utf-8")
            with (output / (name + "-compile.log")).open("w", encoding="utf-8") as log:
                subprocess.run([javac, "--release", "17", "-encoding", "UTF-8", "-d", str(destination),
                                "@" + str(arguments)], stdout=log, stderr=subprocess.STDOUT, check=True)
        for file in all_classes.rglob("*.class"):
            relative = file.relative_to(all_classes)
            if not (production / relative).exists():
                target = fixtures / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(file, target)
        packaged = runtime / "KFC-Invaders.jar"
        subprocess.run([jar, "--create", "--file", str(packaged), "--main-class", "engine.Core",
                        "-C", str(production), ".", "-C", str(ROOT / "res"), "."], check=True)
        service = fixtures / "META-INF/services/endless.GameplayExtension"
        service.parent.mkdir(parents=True, exist_ok=True)
        for suite in (args.suite or DISPLAY + (REGRESSION if args.full else []) + (NATIVE if args.native else [])):
            folder = runtime / ("connected-check-" + suite.replace(".", "-"))
            working = folder / "empty-working-directory"
            working.mkdir(parents=True)
            local_jar = folder / "KFC-Invaders.jar"
            shutil.copy2(packaged, local_jar)
            if suite == "EffectIntegrationTest":
                shutil.copytree(ROOT / "res", working / "res")
            previews = output / suite
            previews.mkdir(exist_ok=True)
            service.write_text("engine.ConnectedSystemsChecks$Probe\n" if suite == "engine.ConnectedSystemsChecks" else "", encoding="ascii")
            with (output / (suite + ".log")).open("w", encoding="utf-8") as log:
                result = subprocess.run([java, "-Djava.awt.headless=" + str(suite not in NATIVE).lower(),
                                         "-Duser.language=en", "-cp", str(local_jar) + os.pathsep + str(fixtures),
                                         suite, str(previews)], cwd=working, stdout=log, stderr=subprocess.STDOUT,
                                        timeout=120)
            results.append({"suite": suite, "exit_code": result.returncode})
            (output / "results.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
            print(("PASS " if result.returncode == 0 else "FAIL ") + suite, flush=True)
            if result.returncode:
                raise SystemExit("See " + str(output / (suite + ".log")))
        shutil.copy2(packaged, output / "checked-production.jar")
    print("Logs and previews: " + str(output))


if __name__ == "__main__":
    main()
