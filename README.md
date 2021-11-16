# xnat-web mirror
This is a selective mirror of the main XNAT respository at https://bitbucket.org/xnatdev/xnat-web/src

It contains:

* This branch (``gh-actions``) which is a bare/orphan branch which contains actions required to patch and release the xnat-web WAR file.
* Other mirrored release branches from the main ``xnat-web`` repository

Steps to add new releases are:

1. git clone --single-branch --branch releases/X.X https://bitbucket.org/xnatdev/xnat-web.git
2. cd xnat-web/
3. git push --mirror https://github.com/Australian-Imaging-Service/xnat-web.git

...where ``X.X`` is the release you wish to clone/mirror

The reason why this selective release mirroring is required is becakse the GitHub checkout action does not support checkouts from Git repositories other than GitHub.

Patches to the xnat-web source code are applied from the [xnat-build patches](https://github.com/Australian-Imaging-Service/xnat-build/tree/main/patches) directory. Currently only patching ``xnat-web`` is supported.
