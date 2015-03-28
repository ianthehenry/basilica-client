# What is this

It's the first [Basilica](https://github.com/ianthehenry/basilica) client! You can see a live demo running at https://basilica.horse.

# For hacking

You'll need some dependencies. You can grab them with homebrew and [homebrew cask](https://github.com/caskroom/homebrew-cask):

    $ brew install node ncat leiningen
    $ brew cask install java

Then grab [`httprintf`](https://github.com/ianthehenry/httprintf) and put it on your PATH.

Now you can install the client dependencies:

    $ npm install

And you're done! You can start leiningen (for live recompiling) and the dev server (for actually serving files) like this:

    $ ./start

Ta-da!
