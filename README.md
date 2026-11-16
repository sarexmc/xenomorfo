# Xenomorph (Fabric 1.21.1)

Un xenomorfo que vive en las cuevas del Overworld. Usa **tu modelo y tus animaciones** (idle, idle2, running, sprinting, roar) del zip `xenomorph-runner`.

## Compilar
Requisitos: JDK 21.
1. Abre esta carpeta en IntelliJ IDEA (importa como proyecto Gradle) **o** en terminal: `gradle wrapper --gradle-version 8.10` y luego `./gradlew build`.
2. El jar queda en `build/libs/xenomorph-1.0.0.jar`. Ponlo en `mods/` junto a **Fabric API 0.102.0+1.21.1** (o superior para 1.21.1).
3. Para probar: `./gradlew runClient`, y en el mundo `/summon xenomorph:xenomorph` o el huevo generador (pestaña de huevos).

## Mecánicas
| Mecánica | Cómo funciona |
|---|---|
| **Aparición** | Solo bajo tierra (Y < 48, sin cielo visible), en el Overworld. Peso 4, de 1 en 1. |
| **Oído** | Te detecta a 48 bloques si corres, 22 si caminas y solo 6 si vas agachado. |
| **Grito** | Al verte a más de 6 bloques ruge 4 s: Oscuridad 4 s + Lentitud II 1.5 s a los jugadores a 24 bloques. |
| **Escalada** | Navegación tipo araña: sube paredes. En la pared se renderiza **tumbado**, vientre contra la roca y cabeza hacia arriba. |
| **Ácido** | A 6–28 bloques escupe ráfaga de 3 globos en arco: 5 de daño + Veneno II, y dejan un **charco** 8 s que daña a quien lo pise. |
| **Agarre** | A menos de ~2 bloques te monta sobre él. Hay que pulsar **W/A/S/D** en la secuencia de 5 teclas que aparece en la barra de acción. Tecla equivocada = 2 de daño; tardar más de 1.5 s = 4. Al completarla te sueltas, te da velocidad y el alien queda aturdido 2 s. |
| **Vida / daño** | 60 de vida, 6 de daño melee. Ajustable en `XenomorphEntity.createXenoAttributes()`. |

## Archivos clave
- `XenomorphEntity.java` – IA, estados, grito, ácido, agarre.
- `XenoGoals.java` – objetivos de IA.
- `client/XenoModel.java` – lector del modelo/animaciones y dibujo.
- `client/XenomorphRenderer.java` – postura en pared (`WALL_PIVOT_Y/Z`) y selección de animación.
- `tools/convert_gltf.py` – regenera `xeno_model.json` si cambias el modelo: `python3 tools/convert_gltf.py tools/xenomorph_source.gltf src/main/resources/xeno_model.json`

## Si algo no cuadra
- **Agarre no detecta teclas**: el servidor lee `forwardSpeed/sidewaysSpeed` del jugador montado (el cliente envía ese input mientras va montado).
- **Modelo flotando o hundido en la pared**: toca `WALL_PIVOT_Y` y `WALL_PIVOT_Z` en el renderer.
- **Sonidos**: usa sonidos vanilla combinados (rugido de Ravager + grito de Ghast + rugido de Warden). Para sonidos propios añade `.ogg` y un `sounds.json`.
