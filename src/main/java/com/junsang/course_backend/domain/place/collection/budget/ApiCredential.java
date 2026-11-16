package com.junsang.course_backend.domain.place.collection.budget;

/** id는 비밀이 아닌 설정 식별자이며 키 원문은 DB에 저장하지 않는다. */
public record ApiCredential(String id, String keyId, String key) {
    public boolean configured(ApiScope scope) {
        return id != null && !id.isBlank() && key != null && !key.isBlank()
                && (scope != ApiScope.NAVER_SEARCH || keyId != null && !keyId.isBlank());
    }

    @Override
    public String toString() {
        return "ApiCredential[id=" + id + "]";
    }
}
