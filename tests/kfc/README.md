# Optional KFC boss integration checks

These harnesses target the October 18 merge of course main 5de5f8d into boss PR #142. They are kept on this validation branch, outside the course PR and shared CI.

Use Java 17. Run from the repository root and put compiled classes in a fresh temporary directory so generated saves cannot affect real player profiles:

```sh
check_dir=$(mktemp -d)
mkdir -p "$check_dir/classes" "$check_dir/screenshots"
javac -Xlint:all -d "$check_dir/classes" $(find src -name '*.java')
javac -cp "$check_dir/classes" -d "$check_dir/classes" tests/kfc/*.java
java -Djava.awt.headless=true -cp "$check_dir/classes:res" KfcBossChecks "$check_dir/screenshots"
java -Djava.awt.headless=true -cp "$check_dir/classes:res" KfcSyncChecks "$check_dir/screenshots"
java -Djava.awt.headless=true -cp "$check_dir/classes:res" KfcBurstChecks "$check_dir/screenshots"
java -Djava.awt.headless=true -cp "$check_dir/classes:res" KfcOct18Checks "$check_dir/screenshots" res/graphics
```

Expected totals: 62 boss checks, 27 sync checks, 61 burst checks and 46 October 18 merge checks (196 total). The harnesses use a test-only offscreen frame adapter and reflection; Java 17 Unsafe warnings are expected. They do not replace a human playthrough or assess balance.
