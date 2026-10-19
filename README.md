# Recordatorios Contextuales - Actividad 1

Aplicación Android desarrollada en Kotlin con Jetpack Compose y Material 3 para la gestión de recordatorios contextuales, categorías y prioridades.

---

## 👨‍💻 Datos del Estudiante

- **Estudiante:** Elías Walter Deguel Escalante
- **Materia:** Programación Mobile
- **Actividad:** Actividad 1 - Primer entorno, modelado y UI inicial

---

## 🛠️ Tecnologías y Herramientas

- **Lenguaje:** Kotlin
- **UI Framework:** Jetpack Compose + Material 3
- **Build System:** Gradle (Kotlin DSL `.kts`)
- **Arquitectura / Estructura de Datos:** Data Classes inmutables, Sealed Classes, Lists, Sets y Maps

---

## 📌 Contenido de la Actividad 1

1. **Modelado de Datos (`ReminderModels.kt`):**
   - `Priority`: Sealed class con etiquetas y colores definidos (`High`, `Medium`, `Low`).
   - `ReminderStatus`: Sealed class para gestionar estados (`Pending`, `InProgress`, `Completed`).
   - `Category`: Data class para agrupar recordatorios.
   - `Reminder`: Data class inmutable con atributos opcionales (`description`, `locationOrNotice`).

2. **Colecciones y Datos Simulados (`ReminderRepository.kt`):**
   - Uso de `Set` para garantizar categorías únicas.
   - Uso de `Map` para mapear colores por categoría.
   - Uso de `List` inmutable de recordatorios simulados y métodos de filtrado.

3. **Interfaz de Usuario (`MainActivity.kt`):**
   - Pantalla construida con `Scaffold`, `LazyColumn`, `Card` y componentes de Material 3.
   - Representación visual del título, prioridad, estado, categoría y aviso/ubicación contextual.
