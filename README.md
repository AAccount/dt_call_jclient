# dt_call_jclient

A test java client for my VoIP server.

It establishes both sockets required to make and place a call. It will accept calls and can dial out calls. However, during calls, you must specify an existing amr file to be used to simulate your voice. Any voice data received, is simply recorded to a file for later playback.

Mostly just used for testing purposes to see the server work and to simulate calls because I don't like to endlessly talk to myself . Much easier to convert a chunk of music into amr, concatenate them and run them all through and listen for skips, jumps, etc.
