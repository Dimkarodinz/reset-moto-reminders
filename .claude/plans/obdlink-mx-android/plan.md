# Original OBDLink MX Android support - implementation plan

## Validated design

```text
bonded Android device
  -> exact name/profile (`OBDLink MX`)
    -> profile-specific standard SPP socket
      -> STDI hardware gate + STI STN115-family gate
        -> existing exact Tiger profile
          -> existing bounded reads and confirmed writes
```

Critical/high review corrections incorporated before implementation:

1. Original MX is not a CX fallback and receives no BLE code path.
2. Original MX is not MX+. Exact bonded-name matching rejects `OBDLink MX+` before a socket opens.
3. The pairing instructions use the physical Connect button and Android settings; they do not invent a PIN.
4. RFCOMM creation must receive the selected adapter profile. The old factory closure captured the primary vLinker profile; that was harmless while only one Classic profile existed but would be unsafe with two.
5. No motorcycle command changes. Adapter support ends at the existing adapter-ready boundary; all Tiger compatibility/write gates remain intact.
6. Support is experimental because official interface documentation is not a project hardware replay.
7. Release links are changed only after a clean tagged, signed artifact has been published.

## TDD implementation sequence

1. **Map contract**
   - Red: require the original MX map in schema and loader tests.
   - Green: add `obdlink-mx-android.adaptermap.yaml` with exact name, Android-only Classic SPP, physical-button pairing, `STDI` hardware identity and `STI` STN115-family gate.
   - Refactor: keep pairing data descriptive; do not force a fake PIN into runtime behavior.

2. **Classic adapter registry and selection**
   - Red: test that vLinker and original MX both appear with their own profile IDs while MX+ and unrelated devices do not.
   - Green: package and load MX as an additional Android profile.
   - Refactor: mark every non-primary registered adapter consistently as experimental instead of making the flag a BLE-only side effect.

3. **Profile-specific RFCOMM connection**
   - Red: replay MX `STDI`/initialization and assert the transport factory receives the selected MX profile and standard SPP UUID.
   - Green: pass the selected profile into RFCOMM transport construction.
   - Refactor: retain a small, transport-neutral owner boundary and the existing serialized ELM session.

4. **Identity and non-regression coverage**
   - Red: prove an MX `STDI`/`STI` identity reaches adapter-ready while nearby products and incorrect STN replies fail before motorcycle traffic.
   - Green: implement the narrow identity matcher needed by the profile.
   - Refactor: keep vLinker exact identity and CX prefix identity behavior unchanged.

5. **UX, versions and knowledge**
   - Bump Android to 0.10.0 / versionCode 14.
   - Add concise localized pairing guidance and experimental labels in English, German, Spanish, French and Ukrainian.
   - Update root/Android guides and compatibility tables without changing iPhone compatibility.

6. **Verification and review**
   - Run focused tests after each red-green step.
   - Run the main Android unit suite, lint and release build.
   - Review the final diff for profile bypass, MX/MX+ ambiguity, vLinker/CX regressions, transport-factory capture bugs, signing leakage and private artifacts.
   - Do not run or modify the separate research apps unless shared-source compilation reveals a regression.

7. **Publication**
   - Push `develop` from the current released `main` baseline if it does not already exist.
   - Commit and push `feature/obdlink-mx-android`, open a PR to `develop`, inspect checks/diff and merge it.
   - Tag the exact merged develop commit `android-v0.10.0`.
   - Build and verify the maintainer-signed APK/checksum from the clean exact tag, then publish the GitHub Release and verify downloads.
   - Create a website-only update from `main`, point Android download/version/compatibility text to v0.10.0, regenerate localized pages, merge to `main`, and verify GitHub Pages.

## Deferred physical validation

- Power the original OBDLink MX from the Tiger diagnostic port and press its Connect button.
- Pair `OBDLink MX` in Android Bluetooth settings, connect in the app and verify adapter identity/readiness.
- Validate dashboard and DTC reads before either write.
- Validate service reset and Beta DTC clear separately under the existing motorcycle safety procedure.
- Remove the experimental label only after retained project-app evidence succeeds.
