# WakeWay — Build From Your Phone

WakeWay is prepared for a cloud-build workflow, so you can work from Android Chrome without owning a computer.

## Simplest route

1. Create a new GitHub repository named `WakeWay`.
2. Upload the contents of this folder to the repository.
3. Open **Actions** → **WakeWay Android Build**.
4. Tap **Run workflow**.
5. Wait for the build to finish.
6. Open the workflow run → **Artifacts**.
7. Download `wakeway-debug-apk` to your Android phone and install it for testing.

The same workflow also produces a release AAB. The AAB is a build artifact; before Play Store production release, configure a proper upload key / Play App Signing and complete Play Console compliance.

## Important

The app's core destination alarm is local-first. AI, weather, train, family cloud, chat, social and other cloud services remain optional until their provider settings are configured.

Do not put secret provider keys inside the Android project. Secrets belong on the server/Cloudflare side.
