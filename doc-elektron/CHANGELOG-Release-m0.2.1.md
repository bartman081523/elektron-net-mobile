# Elektron Net: `elektron-net-mobile` Release m0.2.1

- **Version:** m0.2.1
- **Date:** November 1, 2026
- **Audience:** Testers, maintainers and users who want to know what a release added
- **Reference implementation:** [`elektron-net-mobile`](https://github.com/kutlusoy/elektron-net-mobile) `app/`, release names look like `v<node version>-m0.2.1` (see `guideline-distribution-and-signing.md` section 8.1)
- **See also:** [`CHANGELOG-Release-m0.2.0.md`](./CHANGELOG-Release-m0.2.0.md)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

This file lists the features this release added. Corrections are not listed.

---

**Status:** in preparation, branch `m0.2.1`. It continues from `m0.2.0`, the wallet version is 0.2.1.

## Extra word (25th word) for the recovery phrase

- When a wallet is created or restored, an optional switch "Use an extra word (25th word), advanced" adds a BIP 39 passphrase to the recovery phrase. The seed, and with it every address, is derived from the words and the extra word together. Someone who finds only the words cannot open the wallet.
- It is not the wallet passphrase. The wallet passphrase unlocks the wallet on the phone for sending, the extra word is part of the keys themselves.
- When creating, the extra word is entered twice. When restoring, a wrong or missing extra word opens a different, empty wallet without an error, which the screen says. The extra word is never stored, so it has to be kept apart from the words. Without it the coins cannot be restored.
- Wallets without an extra word are unchanged. An empty extra word gives the same seed as before, so existing wallets, backups, watch-only wallets and escrows need no change. An extra word cannot be added to an existing wallet, because it makes a different wallet.
- Escrow keys are derived from the master key of the wallet, so they follow the extra word.
