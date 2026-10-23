"""
Script de ingesta para la base de conocimiento de HydroGest.

Uso:
    python scripts/ingest.py --dry-run
"""

import argparse
import os
import sys
from pathlib import Path

# Agregar directorio raíz al PYTHONPATH si es necesario
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from dotenv import load_dotenv
from app.services.chunker import procesar_conocimiento

load_dotenv()


def ejecutar_dry_run(directorio: str | None = None) -> None:
    dir_objetivo = directorio or os.getenv("CONOCIMIENTO_DIR", "conocimiento")
    ruta_dir = Path(dir_objetivo)

    print("=" * 70)
    print(" INGESTA DE CONOCIMIENTO (MODO --dry-run) ")
    print("=" * 70)
    print(f"Directorio fuente: {ruta_dir.resolve()}\n")

    try:
        chunks, advertencias = procesar_conocimiento(directorio=ruta_dir)
    except Exception as e:
        print(f"[ERROR FATAL AL PROCESAR CHUNKS]\n{e}", file=sys.stderr)
        sys.exit(1)

    archivos_unicos = {chunk.metadata["archivo"] for chunk in chunks}
    num_archivos = len(archivos_unicos)
    num_chunks = len(chunks)

    # Conteo de palabras por chunk (sobre el campo texto completo del chunk)
    conteo_palabras = [len(chunk.texto.split()) for chunk in chunks]
    palabras_min = min(conteo_palabras) if conteo_palabras else 0
    palabras_max = max(conteo_palabras) if conteo_palabras else 0
    palabras_prom = (
        sum(conteo_palabras) / len(conteo_palabras) if conteo_palabras else 0.0
    )

    print("--- RESUMEN DE PROCESAMIENTO ---")
    print(f"Número de archivos procesados: {num_archivos}")
    print(f"Número de chunks generados:    {num_chunks}")
    print(f"Palabras por chunk (mínimo):   {palabras_min}")
    print(f"Palabras por chunk (promedio): {palabras_prom:.1f}")
    print(f"Palabras por chunk (máximo):   {palabras_max}")
    print()

    # Advertencias
    print("--- ADVERTENCIAS ---")
    if advertencias:
        for adv in advertencias:
            print(f"  [!] {adv}")
    else:
        print("  Ninguna advertencia detectada.")
    print()

    # 3 Chunks de ejemplo completos
    print("--- 3 CHUNKS DE EJEMPLO COMPLETOS ---")
    if num_chunks >= 3:
        # Tomar primero, intermedio y último
        ejemplos_indices = [0, num_chunks // 2, num_chunks - 1]
    else:
        ejemplos_indices = list(range(num_chunks))

    for num_ejemplo, idx in enumerate(ejemplos_indices, start=1):
        chunk = chunks[idx]
        print(f"\n[Ejemplo {num_ejemplo} | Índice #{idx + 1}]")
        print(f"ID: {chunk.id}")
        print("Metadata:")
        for k, v in chunk.metadata.items():
            print(f"  - {k}: {v}")
        print("Texto completo:")
        print("-" * 50)
        print(chunk.texto)
        print("-" * 50)

    print("\n" + "=" * 70)
    print(" [OK] Modo dry-run completado: No se llamó a ninguna API ni se escribió en disco.")
    print("=" * 70)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Ingesta y chunking de la base de conocimiento para HydroGest.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Ejecuta solo la lectura y el chunking sin llamar a APIs ni persistir en base de datos.",
    )
    parser.add_argument(
        "--dir",
        type=str,
        default=None,
        help="Ruta alternativa de la carpeta de conocimiento (por defecto CONOCIMIENTO_DIR o 'conocimiento').",
    )

    args = parser.parse_args()

    if args.dry_run:
        ejecutar_dry_run(directorio=args.dir)
    else:
        print(
            "Aviso: La indexación en ChromaDB y generación de embeddings aún no están habilitadas.\n"
            "Ejecute con el flag '--dry-run' para verificar la lectura y fragmentación del conocimiento:\n"
            "    python scripts/ingest.py --dry-run\n"
        )


if __name__ == "__main__":
    main()
