package com.jostin.launcher;

// Lo que expone ShellService (corre como «shell» dentro de Shizuku)
interface IShell {
    // Shizuku lo llama para cerrar el servicio; el número es el que pide su API
    void destroy() = 16777114;

    // Corre un comando con sh -c; regresa «código\nsalida»
    String run(String cmd) = 1;

    // Crea (o reemplaza) y prende un FabricatedOverlay sobre «android». spec = "recurso=AARRGGBB;…"
    // Regresa "ok N" o el error
    String fabricate(String name, String spec) = 2;

    // Lo quita por completo (los colores regresan a los de Android)
    String unfabricate(String name) = 3;

    // Bloquea partes de la barra de Android (flags de StatusBarManager; 0 = quitar todo).
    // El bloqueo queda amarrado a «token», que vive en el launcher: si Jostin se cierra o truena,
    // Android lo quita solo.
    String statusBar(IBinder token, int flags) = 4;
}
