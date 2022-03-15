# Cheogram Android

This is a fork of [Conversations](https://conversations.im) to implement features of use to the [Sopranica](https://soprani.ca) project.

The Cheogram Android app allows you to join a worldwide communication network.  It especially focuses on features useful to users who want to contact those on other networks as well, such as SMS-enabled phone numbers.

Based on the app Conversations, but with unique features:

* Messages with both media and text, including animated media
* Unobtrusive display of subject lines, where present
* Links to known contacts are shown with their name
* Integrates with gateways' add contact flows
* When using a gateway to the phone network, integrate with the native Android Phone app
* Address book integration

Where to get service:

Cheogram Android requires you have an account with a Jabber service.  You can run your own service, or use one provided by someone else, for example: https://snikket.org/hosting/

Art in screenshots is from https://www.peppercarrot.com by David Revoy, CC-BY. Artwork has been modified to crop out sections for avatars and photos, and in some cases add transparency. Use of this artwork does not imply endorsement of this project by the artist.

## Getting Help

If you have any questions about this app, or wish to report a bug, please come by the chatroom at  xmpp:discuss@conference.soprani.ca?join or [on the web](https://anonymous.cheogram.com/discuss@conference.soprani.ca).

## Contributing

If you have code or patches you wish to contribute, the maintainer's preferred mechanism is a git pull request.  Push your changes to a git repository somewhere, for example:

    git remote rename origin upstream
    git remote add origin git@git.sr.ht:~yourname/cheogram-android
    git push -u origin master

Then generate the pull request:

    git fetch upstream master
    git request-pull -p upstream/master origin

And copy-paste the result into a plain-text email to: dev@singpolyma.net

You may alternately use a patch-based approach as described on https://git-send-email.io

Contributions follow an inbound=outbound model -- you (or your employer) keep all copyright on your patches, but agree to license them according to this project's COPYING file.
