# icfpc2018

## Working with the code

To assemble the project into a JAR do

```bash
$ ./gradlew assemble
```

Running visualisation:

```bash
ARGS="<args>" ./gradlew :run
```

Running batch solving:

```bash
ARGS="batch <batch_name>.batch sculptor" ./gradlew run
```

For harder disassembly problems we re-run from the disasm-via-reasm git branch. The sculptor strategy first fills the bounding box of a model with GFill, and then uses a line of bots to "sculpt" it into the target model by going forward and voiding all and then refilling the correct voxels. Harmonics is flipped whenever we're not grounded during that process.
