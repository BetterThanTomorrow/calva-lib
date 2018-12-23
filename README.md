# calva-lib

Some of the functionality of the [Calva] family of Visual Studio Code extensions is provided by this library. It is built as an npm module, [@cospaia/calva-lib](https://www.npmjs.com/package/@cospaia/calva-lib), but not meant for public consumption.


## How to Contribute to calva-lib

1. Clone your fork
1. Most often you'll be branching off of `master` (the main branch).
1. `npm install` (This will install, amongst other things, `shadow-cljs`)
1. Open the project root directory in VS Code. (You are using VS Code and Calva, right?)

The dev process is like so:
1. In VS Code: **Run Build Task…**. This will run `shadow-cljs` and make it watch `:test` and `:calva-lib`.
1. Check the task Output pane and notice that tests are run.
1. Connect Calva and choose the `node-repl` for your CLJS repl (it won't work with any of the build repls, unfortunately).
1. Hack away. Every time you save, the tests are run.
   1. Add/remove/modify any relevant test.
1. Test the changes in the actual extension (see below about that setup).
   1. Assuming it is setup this means switching to the *Calva* window and restart the extension host process (then switch to the extension host window and do your manual testing).

For testing the changes to the actual extensions, see the relevant **How to Contribute** page:

* [Calva](https://github.com/BetterThanTomorrow/calva/wiki/How-to-Contribute)
* [Calva Formatter](https://github.com/BetterThanTomorrow/calva-fmt/wiki/How-to-Contribute)
* (Soon also [Calva Paredit](https://github.com/BetterThanTomorrow/calva-paredit))

## Happy hacking! ❤️
Please feel invited to join the [`#calva-dev` channel](https://clojurians.slack.com/messages/calva-dev/) channel on the Clojurians Slack.
