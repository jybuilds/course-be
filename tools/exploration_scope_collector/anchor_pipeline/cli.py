from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

from .pipeline import CourseAnchorPipeline


PROJECT_DIR = Path(__file__).resolve().parents[1]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="course-anchor-pipeline",
        description="공식·공공 원문을 CourseAnchor와 추천 규칙 후보 JSON으로 정제합니다.",
    )
    parser.add_argument("command", choices=["init-city", "collect", "normalize", "evaluate", "run"])
    parser.add_argument("--city", required=True, help="사용자가 선택하는 큰 지역명. 현재 예: 서울")
    parser.add_argument(
        "--workspace",
        type=Path,
        default=PROJECT_DIR / "data",
        help="중간 결과와 최종 JSON 저장 폴더",
    )
    parser.add_argument(
        "--lm-studio-url",
        default=os.getenv("LM_STUDIO_URL", "http://localhost:1234"),
        help="LM Studio OpenAI 호환 API 주소",
    )
    parser.add_argument(
        "--model",
        default=os.getenv("LM_STUDIO_MODEL"),
        help="LM Studio 모델 ID. 생략 시 사용 가능한 모델이 하나일 때 자동 선택",
    )
    parser.add_argument("--refresh", action="store_true", help="기존 수집·정제·평가 결과를 다시 생성")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    pipeline = CourseAnchorPipeline(
        project_dir=PROJECT_DIR,
        workspace_dir=args.workspace.resolve(),
        lm_studio_url=args.lm_studio_url,
        model=args.model,
        refresh=args.refresh,
    )
    try:
        if args.command == "init-city":
            result = pipeline.initialize_city(args.city)
        elif args.command == "collect":
            results = pipeline.collect(args.city)
            result = f"원문 배치 {len(results)}개"
        elif args.command == "normalize":
            results = pipeline.normalize(args.city)
            result = f"정제 배치 {len(results)}개"
        elif args.command == "evaluate":
            result = pipeline.evaluate(args.city)
        else:
            result = pipeline.run(args.city)
        print(f"완료: {result}")
        return 0
    except (OSError, RuntimeError, ValueError) as error:
        print(f"실패: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
