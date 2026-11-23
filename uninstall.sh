#!/bin/sh
# Remove BESHE's user-local installation. Does not touch Java or shell profiles.
set -eu
rm -rf "$HOME/.local/share/beshe" "$HOME/.local/bin/beshe"
printf 'BESHE application removed. History remains at ~/.beshell/history.\n'
printf 'Remove the BESHE PATH lines from ~/.zprofile or ~/.bash_profile if desired.\n'
