# Forge Community Wiki: JavaDocs
[![Build Documentation](https://github.com/forgecommunitywiki/javadocs/workflows/Build%20Documentation/badge.svg)](https://github.com/forgecommunitywiki/javadocs/actions?query=workflow%3A%22Build+Documentation%22)
[![Website](https://img.shields.io/website?url=https%3A%2F%2Fforgecommunitywiki.github.io%2Fjavadocs%2F1.16)](https://forgecommunitywiki.github.io/javadocs/)
[![Discord](https://img.shields.io/discord/756881288232828968?label=Discord&logo=discord&logoColor=ffffff)](https://discord.gg/Nn42eAh)


> The javadocs companion to the wiki.

This is a community driven documentation effort for Minecraft Forge, the popular modding API for Minecraft.

The generated docs are available here: [website](https://forgecommunitywiki.github.io/javadocs/)

## Contributing
Contributing to the project is easy, just follow these steps:
1. **Fork** the repository and clone it to your local machine.
2. In the repository folder, run the gradle tasks `setup` to setup the javadocs workspace.
   - for **Windows**: Open a command prompt, then run `gradlew setup`.
   - for **\*nix** systems: Open a terminal, then run `./gradlew setup`.
3. Add or modify the javadocs comments for the source files under `workspace/src/forge/java`.
   - To check the results of your changes, run the `assembleJavadocs` task again and see the outputs in `out`.
4. Commit and push your changes to your fork, then make a Pull Request to the main repository on GitHub.

Your PR will be reviewed by the maintainers or members of the triage team. We welcome any contribution, big or small, so do not fear making a PR for e.g. a spelling correction (though we do prefer if you gather a lot of corrections into one PR).

## Licensing
This project and its files are licensed under [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/) (see the `LICENSE.txt` file for the full legal text).

By contributing to this project, you agree to have your contributions stored and distributed under the same license.
