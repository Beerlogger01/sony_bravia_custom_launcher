# Build

Use JDK 17, Android SDK 34 and Gradle 8.9. Clone the public source using `git clone --recurse-submodules`. The legacy Android library is pinned to its upstream Git commit. Use `gradle -p bravia/android testDebugUnitTest assembleDebug` for development. APK identities are house.bravia.home and house.bravia.panel. Check any deliverable with `apksigner verify --min-sdk-version 24 --print-certs` and `aapt dump badging`. Release builds for existing installations require the original signing certificate. A normal development checkout uses the local Android debug identity and cannot reproduce that signature without the original key. CI publishes no signed release.

Run the scoped HTTPS API tests using Python 3.14, Home Assistant 2026.11.1, OpenSSL and `PYTHONPATH=. python tests/ha_core.py`. This starts a temporary HA instance and only listens on loopback. `python -m unittest discover -s tests` checks the transient credential lifecycle. Run `pwsh -File bravia/windows/tests/hdmi.ps1` and `python bravia/integration/test_windows.py` for the updater. No production credentials or hardware are needed.

## Marketplace review

The validation action inspects repository-wide settings in addition to the branch. The existing upstream license notice is in this PR. GitHub detects repository licensing from the default branch. The repository About description and topics must also be configured by an owner for catalog eligibility. Suggested topics are android-tv, home-assistant and custom-integration. Manual RC installation works independently of catalog review.

For an owner-approved prerelease, build with the existing private identity, verify both APK certificate digests and package IDs, and compute SHA256SUMS locally. The update ZIP carries the two APKs under apk/, PowerShell scripts under windows/, and the update guide. Confirm the pinned APK digests in update.ps1 when preparing a new build. Attach the APKs, checksum file, update ZIP and HA custom_components ZIP to a *draft prerelease* on GitHub. Keep signing material and user backups on the build machine. Publication and stable tagging are separate owner decisions.
