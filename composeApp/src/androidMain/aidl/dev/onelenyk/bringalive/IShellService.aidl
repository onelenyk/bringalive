package dev.onelenyk.bringalive;

interface IShellService {
    void destroy() = 16777114; // Required by Shizuku server

    /**
     * Execute a shell command with ADB-level privileges.
     * Returns "exitCode\nstdout\0stderr" (null-byte separator between stdout and stderr).
     */
    String exec(String command) = 1;
}
