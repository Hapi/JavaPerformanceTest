# JavaPerformanceTest
A single Java file (`PerformanceTest.java`) for creating threaded Java performance test on a computer.


# Executing a test

1. Compile `PerformanceTest.java`
    ```sh
    javac PerformanceTest.java
    ```

2. Execute test
    ```sh
    java PerformanceTest
    ```

3. Get help to see more test options
    ```sh
    java PerformanceTest --help
    ```


# How PerformanceTest is done

CPU test looks for performance numbers in various number of threads. File test creates, reads and
deletes 200 text files totalling 2 000 000 000 bytes (i.e. 10 000 000 bytes per file). Files are
created in a separate target directory named `__target__`.
