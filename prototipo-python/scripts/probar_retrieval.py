"""Evalúa retrieval semántico contra las preguntas del conjunto de evaluación."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from statistics import fmean
from typing import Any

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from app.services.embeddings import EmbeddingError, embed_query
from app.services.vector_store import buscar

TOP_K = 5


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Prueba la búsqueda vectorial de HydroGest.")
    parser.add_argument(
        "--pregunta",
        help="consulta individual; no lee evaluacion/preguntas.json",
    )
    return parser.parse_args()


def _load_questions() -> list[dict[str, Any]]:
    path = PROJECT_ROOT / "evaluacion" / "preguntas.json"
    try:
        with path.open(encoding="utf-8") as file:
            questions = json.load(file)
    except (OSError, json.JSONDecodeError) as exc:
        raise RuntimeError(f"No se pudo leer '{path}': {exc}") from exc
    if not isinstance(questions, list):
        raise RuntimeError(f"'{path}' debe contener una lista de preguntas.")
    return questions


def _search(question: str) -> list[dict[str, Any]]:
    return buscar(embed_query(question), TOP_K)


def _print_results(results: list[dict[str, Any]]) -> None:
    for position, result in enumerate(results, start=1):
        print(f"  {position}. {result['id']} (similitud={result['similitud']:.4f})")


def _run_single(question: str) -> int:
    try:
        results = _search(question)
    except (EmbeddingError, ValueError, RuntimeError) as exc:
        print(f"[ERROR] No se pudo ejecutar la búsqueda: {exc}", file=sys.stderr)
        return 1
    print(f"Pregunta: {question}")
    _print_results(results)
    return 0


def _first_hit_position(
    results: list[dict[str, Any]], expected_files: list[str]
) -> int | None:
    expected = set(expected_files)
    for position, result in enumerate(results, start=1):
        filename = str(result["metadata"].get("archivo", ""))
        if Path(filename).stem in expected:
            return position
    return None


def _format_rate(value: int, total: int) -> str:
    percentage = value / total * 100 if total else 0
    return f"{value}/{total} ({percentage:.1f}%)"


def _stats(values: list[float]) -> str:
    if not values:
        return "sin datos"
    return f"mínimo={min(values):.4f}, promedio={fmean(values):.4f}, máximo={max(values):.4f}"


def _run_evaluation() -> int:
    try:
        questions = _load_questions()
    except RuntimeError as exc:
        print(f"[ERROR] {exc}", file=sys.stderr)
        return 1

    in_scope = [question for question in questions if question.get("tipo") == "en_alcance"]
    out_of_scope = [
        question for question in questions if question.get("tipo") == "fuera_de_alcance"
    ]
    hits = {1: 0, 3: 0, 5: 0}
    in_scope_scores: list[float] = []
    out_of_scope_scores: list[float] = []
    failures: list[tuple[dict[str, Any], list[dict[str, Any]]]] = []

    for index, question in enumerate(questions, start=1):
        text = question.get("pregunta")
        if not isinstance(text, str) or not text.strip():
            print(f"[ERROR] Pregunta {index} no tiene un campo 'pregunta' válido.", file=sys.stderr)
            return 1
        try:
            results = _search(text)
        except (EmbeddingError, ValueError, RuntimeError) as exc:
            print(f"[ERROR] Falló la pregunta {index}: {exc}", file=sys.stderr)
            return 1

        print(f"\n{index}. {text}")
        _print_results(results)
        if question.get("tipo") == "en_alcance":
            position = _first_hit_position(results, question.get("archivos_esperados", []))
            in_scope_scores.append(results[0]["similitud"] if results else 0.0)
            if position is None:
                print("  Resultado: FALLA (no aparece un archivo esperado en top-5)")
                if question.get("nota"):
                    print(f"  Nota: {question['nota']}")
                failures.append((question, results))
            else:
                for limit in hits:
                    if position <= limit:
                        hits[limit] += 1
                band = "puesto 1" if position == 1 else "top-3" if position <= 3 else "top-5"
                print(f"  Resultado: acierto en {band} (puesto {position})")
        elif question.get("tipo") == "fuera_de_alcance":
            best = results[0] if results else {"id": "sin resultados", "similitud": 0.0}
            out_of_scope_scores.append(float(best["similitud"]))
            print(
                f"  Fuera de alcance: mejor similitud={best['similitud']:.4f}, "
                f"id={best['id']}"
            )
        else:
            print(f"[ERROR] Tipo inválido en la pregunta {index}: {question.get('tipo')}", file=sys.stderr)
            return 1

    print("\n=== Resumen ===")
    total_in_scope = len(in_scope)
    print(f"Aciertos top-1: {_format_rate(hits[1], total_in_scope)}")
    print(f"Aciertos top-3: {_format_rate(hits[3], total_in_scope)}")
    print(f"Aciertos top-5: {_format_rate(hits[5], total_in_scope)}")
    print("\nSimilitud del puesto 1:")
    print(f"  en_alcance: {_stats(in_scope_scores)}")
    print(f"  fuera_de_alcance: {_stats(out_of_scope_scores)}")
    print("\nPreguntas fallidas:")
    if not failures:
        print("  Ninguna.")
    else:
        for question, results in failures:
            print(f"\n- {question['pregunta']}")
            if question.get("nota"):
                print(f"  Nota: {question['nota']}")
            _print_results(results)
    return 0


def main() -> int:
    args = _parse_args()
    return _run_single(args.pregunta) if args.pregunta else _run_evaluation()


if __name__ == "__main__":
    raise SystemExit(main())
