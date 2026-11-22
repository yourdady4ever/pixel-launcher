package com.jostin.launcher;

// Lo que expone ShellService (corre como «shell» dentro de Shizuku)
interface IShell {
    // Shizuku lo llama para cerrar el servicio; el número es el que pide su API
    void destroy() = 16777114;

    // Corre un comando con sh -c; regresa «código\nsalida»
    String run(String cmd) = 1;
}
