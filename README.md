# What is this

It's the first [Basilica](https://github.com/ianthehenry/basilica) client!

# For hacking

You'll need `bower`, `npm`, and `brunch`.

    $ brew install node
    $ npm install -g bower brunch

Then put `/usr/local/share/npm/bin` on your PATH, if it isn't already.

I also recommend `terminal-notifier`, but it's not strictly required:

    $ brew install terminal-notifier

You also need ClojureScript:

    $ brew install leiningen

Now you can install the client dependencies:

    $ npm install --dev
    $ bower install

And you're done!

Start `lein cljsbuild auto dev`, then start `brunch watch --server`, and navigate to `localhost:3333`. Ta-da!
