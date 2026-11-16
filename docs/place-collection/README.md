# 장소 수집 통합 가이드

- GUIDE.md: 실제 구현 기준의 편집 가능한 통합 가이드.
- CHANGE_REPORT.md: 이번 변경, 파일 목록, 검증 결과와 배포 전 확인 사항.
- PROGRESS.md: 작업 재개를 위한 진행 기록.
- OMNIROUTE_EVIDENCE.md: 설치 소스 확인과 미검증 범위.
- verification-summary.json: 최종 테스트 결과.
- render_guide.py: Markdown 원본을 PDF로 재생성하는 도구.

재생성 예시(저장소 루트, reportlab이 설치된 Python):

```sh
python3 docs/place-collection/render_guide.py --output output/pdf/place-collection-guide.pdf --font '/Library/Fonts/Arial Unicode.ttf'
```

Linux에서는 한글을 포함하는 TrueType 글꼴 경로를 --font에 지정한다.
PDF 수정 후에는 pdftoppm 등으로 페이지를 렌더링해 표 잘림과 한글 표시를 확인한다.

재현 테스트는 운영 DB 대신 별도 PostgreSQL DB 이름 course_recovery_test를 사용한다.
CollectionRecoveryIntegrationTest는 DB_URL에 이 이름이 있을 때만 실행되며 테스트 테이블을 초기화한다.
실제 모델 호출 테스트는 별도 명시적 실행 조건이 있어 기본 전체 테스트에서는 생략된다.
