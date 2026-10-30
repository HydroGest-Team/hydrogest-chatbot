"""
Servicio de lectura y chunking para la base de conocimiento de HydroGest.

Lee los archivos Markdown (.md) de la carpeta configurada (por defecto 'conocimiento'),
aplica validaciones estrictas y genera chunks estructurados por secciones '##'.
"""

import os
import re
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass
class Chunk:
    """Representa un fragmento de texto procesado con su identificador y metadatos."""

    id: str
    texto: str
    metadata: dict[str, Any]

    def to_dict(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "texto": self.texto,
            "metadata": self.metadata,
        }

    def __getitem__(self, item: str) -> Any:
        return getattr(self, item)


def slugify(text: str) -> str:
    """
    Convierte una cadena en un slug normalizado:
    minúsculas, sin acentos ni caracteres especiales, separado por guiones.
    """
    normalized = unicodedata.normalize("NFD", text)
    without_accents = "".join(c for c in normalized if unicodedata.category(c) != "Mn")
    cleaned = without_accents.lower()
    cleaned = re.sub(r"[^a-z0-9]+", "-", cleaned)
    return cleaned.strip("-")


def _dividir_en_parrafos(cuerpo: str, limite_palabras: int = 350) -> list[str]:
    """
    Divide un texto en bloques de párrafos que no superen aproximadamente el límite de palabras,
    manteniendo la integridad de los párrafos.
    """
    parrafos = [p.strip() for p in re.split(r"\n\s*\n", cuerpo) if p.strip()]
    if not parrafos:
        return []

    bloques: list[str] = []
    bloque_actual: list[str] = []
    palabras_actual = 0

    for p in parrafos:
        palabras_p = len(p.split())
        if bloque_actual and (palabras_actual + palabras_p > limite_palabras):
            bloques.append("\n\n".join(bloque_actual))
            bloque_actual = [p]
            palabras_actual = palabras_p
        else:
            bloque_actual.append(p)
            palabras_actual += palabras_p

    if bloque_actual:
        bloques.append("\n\n".join(bloque_actual))

    return bloques


def procesar_archivo_md(
    ruta_archivo: Path,
    ids_registrados: set[str],
    orden_inicio: int = 1,
) -> tuple[list[Chunk], list[str], int]:
    """
    Procesa un único archivo .md y genera sus correspondientes Chunks.

    Retorna:
        - lista de Chunks
        - lista de advertencias
        - siguiente número de orden
    """
    nombre_archivo = ruta_archivo.name
    stem_archivo = ruta_archivo.stem
    advertencias: list[str] = []

    with open(ruta_archivo, "r", encoding="utf-8") as f:
        contenido_crudo = f.read()

    # 1. Validación: Falla si hay [PENDIENTE] en el texto
    if "[PENDIENTE]" in contenido_crudo:
        raise ValueError(
            f"Error en el archivo '{nombre_archivo}': Se encontró una marca '[PENDIENTE]' en el texto."
        )

    # 2. Ignorar comentarios HTML (<!-- ... -->)
    contenido_sin_comentarios = re.sub(r"<!--.*?-->", "", contenido_crudo, flags=re.DOTALL)

    # 3. Extraer fecha de 'Última actualización:' si existe y removerla del cuerpo
    upd_match = re.search(
        r"^[ \t]*Última actualización:\s*(.*?)[ \t]*$",
        contenido_sin_comentarios,
        flags=re.MULTILINE | re.IGNORECASE,
    )
    actualizacion = upd_match.group(1).strip() if upd_match else ""
    if not actualizacion:
        advertencias.append(
            f"El archivo '{nombre_archivo}' no incluye línea de 'Última actualización:'."
        )

    contenido_limpio = re.sub(
        r"^[ \t]*Última actualización:.*?\n?",
        "",
        contenido_sin_comentarios,
        flags=re.MULTILINE | re.IGNORECASE,
    )

    # 4. Validación: Falla si no tiene título principal '#'
    h1_match = re.search(r"^#[ \t]+(.+)$", contenido_limpio, flags=re.MULTILINE)
    if not h1_match:
        raise ValueError(
            f"Error en el archivo '{nombre_archivo}': No contiene un título principal con formato '# <Título>'."
        )

    titulo_documento = h1_match.group(1).strip()
    if not titulo_documento:
        raise ValueError(
            f"Error en el archivo '{nombre_archivo}': El título principal '#' está vacío."
        )

    # 5. Localizar secciones '##'
    sec_matches = list(re.finditer(r"^##[ \t]+(.+)$", contenido_limpio, flags=re.MULTILINE))
    if not sec_matches:
        raise ValueError(
            f"Error en el archivo '{nombre_archivo}': No contiene ninguna sección con formato '## <Sección>'."
        )

    chunks: list[Chunk] = []
    orden_actual = orden_inicio

    for idx, match in enumerate(sec_matches):
        titulo_seccion = match.group(1).strip()
        inicio_cuerpo = match.end()
        fin_cuerpo = (
            sec_matches[idx + 1].start()
            if idx + 1 < len(sec_matches)
            else len(contenido_limpio)
        )
        cuerpo = contenido_limpio[inicio_cuerpo:fin_cuerpo].strip()

        # Validación: Falla si una sección está vacía
        if not cuerpo:
            raise ValueError(
                f"Error en el archivo '{nombre_archivo}': La sección '## {titulo_seccion}' está vacía."
            )

        slug_seccion = slugify(titulo_seccion)
        if not slug_seccion:
            slug_seccion = f"seccion-{idx + 1}"

        id_base = f"{stem_archivo}::{slug_seccion}"

        # Verificar si supera ~350 palabras para dividir por párrafos
        palabras_cuerpo = len(cuerpo.split())
        if palabras_cuerpo > 350:
            partes = _dividir_en_parrafos(cuerpo, limite_palabras=350)
            advertencias.append(
                f"Sección extensa ('{titulo_seccion}' en '{nombre_archivo}', {palabras_cuerpo} palabras) "
                f"dividida en {len(partes)} partes manteniendo el contexto."
            )
        else:
            partes = [cuerpo]

        # Generar chunk(s)
        for sub_idx, parte in enumerate(partes):
            if len(partes) > 1:
                chunk_id = f"{id_base}-parte-{sub_idx + 1}"
            else:
                chunk_id = id_base

            # Validación: Falla si hay IDs duplicados
            if chunk_id in ids_registrados:
                raise ValueError(
                    f"Error: Se detectó un ID duplicado '{chunk_id}' en el archivo '{nombre_archivo}'."
                )
            ids_registrados.add(chunk_id)

            # Texto del chunk anteponiendo Documento y Sección
            texto_chunk = (
                f"Documento: {titulo_documento}\n"
                f"Sección: {titulo_seccion}\n\n"
                f"{parte}"
            )

            metadata_chunk = {
                "archivo": nombre_archivo,
                "documento": titulo_documento,
                "seccion": titulo_seccion,
                "actualizacion": actualizacion,
                "orden": orden_actual,
            }

            chunks.append(
                Chunk(
                    id=chunk_id,
                    texto=texto_chunk,
                    metadata=metadata_chunk,
                )
            )
            orden_actual += 1

    return chunks, advertencias, orden_actual


def procesar_conocimiento(
    directorio: str | Path | None = None,
) -> tuple[list[Chunk], list[str]]:
    """
    Lee todos los archivos .md en el directorio especificado (o CONOCIMIENTO_DIR),
    valida el contenido y genera la lista consolidada de Chunks.

    Retorna una tupla (chunks, advertencias).
    """
    if directorio is None:
        dir_env = os.getenv("CONOCIMIENTO_DIR", "conocimiento")
        ruta_dir = Path(dir_env)
    else:
        ruta_dir = Path(directorio)

    if not ruta_dir.exists():
        raise FileNotFoundError(f"El directorio de conocimiento no existe: '{ruta_dir}'")

    archivos_md = sorted(ruta_dir.glob("*.md"))
    if not archivos_md:
        raise FileNotFoundError(
            f"No se encontraron archivos .md en el directorio: '{ruta_dir}'"
        )

    todos_los_chunks: list[Chunk] = []
    todas_las_advertencias: list[str] = []
    ids_registrados: set[str] = set()
    orden_global = 1

    for archivo in archivos_md:
        chunks_archivo, advertencias_archivo, orden_global = procesar_archivo_md(
            ruta_archivo=archivo,
            ids_registrados=ids_registrados,
            orden_inicio=orden_global,
        )
        todos_los_chunks.extend(chunks_archivo)
        todas_las_advertencias.extend(advertencias_archivo)

    return todos_los_chunks, todas_las_advertencias


def obtener_chunks(directorio: str | Path | None = None) -> list[Chunk]:
    """
    Función de acceso directo que devuelve exclusivamente la lista de Chunks.
    """
    chunks, _ = procesar_conocimiento(directorio=directorio)
    return chunks
