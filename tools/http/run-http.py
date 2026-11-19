"""`.http` 파일의 요청을 순서대로 실행하고 기대 상태 코드와 비교한다.

HTTP Client가 없는 환경에서 같은 요청 파일을 터미널로 재생하기 위한 도구다.
기대 상태 코드는 요청 제목에 적힌 마지막 HTTP 코드를 쓰고, 없으면 GET은 200, POST는 201로 본다.
앞선 응답이 courseId를 주면 이후 요청의 {{courseId}} 를 그 값으로 바꾼다.

사용법: python3 tools/http/run-http.py http/course-creation.http
"""
import json
import re
import sys
import urllib.error
import urllib.request

# \b 는 한글도 단어 문자로 봐서 "404가"처럼 붙은 코드를 놓치므로, 앞뒤가 숫자가 아닌지만 본다.
STATUS_IN_TITLE = re.compile(r"(?<!\d)(200|201|204|400|404|409|500)(?!\d)")
DEFAULT_STATUS = {"GET": 200, "POST": 201}


def parse(text):
    host_match = re.search(r"^@host\s*=\s*(.+)$", text, re.M)
    host = host_match.group(1).strip() if host_match else ""
    requests = []
    for block in text.split("\n###")[1:]:
        lines = block.strip("\n").split("\n")
        title = lines[0].strip()
        method, url = lines[1].split(" ", 1)
        body = None
        if "" in lines[2:]:
            body = "\n".join(lines[lines.index("", 2) + 1:]).strip() or None
        codes = STATUS_IN_TITLE.findall(title)
        expected = int(codes[-1]) if codes else DEFAULT_STATUS.get(method, 200)
        requests.append((title, method, url.replace("{{host}}", host).strip(), body, expected))
    return host, requests


def send(method, url, body):
    request = urllib.request.Request(
        url,
        method=method,
        data=body.encode() if body else None,
        headers={"Content-Type": "application/json"} if body else {},
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def summarize(data):
    if isinstance(data, list):
        if not data:
            return "[] (후보 없음)"
        return ", ".join(f"{item['name']} {item['distanceMeters']}m {item['score']}점" for item in data)
    if "slots" in data:
        parts = []
        for slot in data["slots"]:
            first = slot["places"][0]["name"] if slot["places"] else "(후보 없음)"
            parts.append(f"{slot['order']}.{slot['role']} {first} 외 {max(len(slot['places']) - 1, 0)}곳")
        return " → ".join(parts)
    if "published" in data and "tags" in data:
        return f"코스 {data['courseId']} 공개={data['published']}, 태그={data['tags']}"
    if "courseId" in data:
        orders = [item["itemOrder"] for item in data["scheduleItems"]]
        return f"코스 {data['courseId']} 저장, 공개={data['published']}, 순서={orders}, 태그={data['generatedTags']}"
    return data.get("code", str(data))


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    host, requests = parse(open(sys.argv[1], encoding="utf-8").read())
    try:
        urllib.request.urlopen(f"{host}/actuator/health", timeout=3)
    except (urllib.error.URLError, OSError):
        print(f"서버가 떠 있지 않습니다 ({host}). 다른 터미널에서 먼저 `make run` 을 실행하세요.")
        return 1

    passed = 0
    course_id = None
    for number, (title, method, url, body, expected) in enumerate(requests, start=1):
        # 공개 전환은 저장된 코스 ID가 필요하므로 앞선 저장 응답의 값을 물려받는다.
        if "{{courseId}}" in url:
            if course_id is None:
                print(f"❌ {title}")
                print("     앞선 요청에서 courseId를 받지 못했습니다.")
                continue
            url = url.replace("{{courseId}}", str(course_id))
        status, data = send(method, url, body)
        if isinstance(data, dict) and "courseId" in data:
            course_id = data["courseId"]
        ok = status == expected
        passed += ok
        print(f"{'✅' if ok else '❌'} {title}")
        print(f"     HTTP {status}{'' if ok else f' (기대 {expected})'}  {summarize(data)}")
    print(f"\n{len(requests)}개 중 {passed}개 통과")
    return 0 if passed == len(requests) else 1


if __name__ == "__main__":
    sys.exit(main())
