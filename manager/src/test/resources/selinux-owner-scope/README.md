# Owner SELinux status focused regression

Run from the repository root:

```powershell
& manager/src/test/resources/selinux-owner-scope/run.ps1
```

The runner compiles the complete production `SELinuxChecker.kt`, this fixture's boundary files,
and the existing four `SELinuxCheckerTest` cases. It uses the cached Kotlin 2.4.20 compiler and
the current Java executable without Gradle or an APK build. `-SourcePath` accepts a frozen before
version; `-OwnerOnly` runs the original public-entry regression for before/after comparison.

The fixture controls Android clock/uid, Hook availability, the already verified Owner snapshot,
and SDK replies. It does not reproduce the native identity producer or Android SELinux. The
local-source cases read real temporary files through the internal IO-only overload; the public
entry continues to read `/sys/fs/selinux/enforce`. The tests preserve the original error/Unknown
result, separate Key and Owner cache identities, and exercise barriers around concurrent reads,
Owner/boot/revision transitions and late replies.
