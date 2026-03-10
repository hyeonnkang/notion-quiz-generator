document.getElementById('sendBtn').addEventListener('click', async () => {
    const status = document.getElementById('status');
    status.innerText = "분석 중...";

    let [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    const url = tab?.url || "";

    const cleanUrl = url.split('?')[0].split('#')[0];
    let pageId = cleanUrl.split('-').pop() || "";
    pageId = pageId.replace(/[^a-fA-F0-9]/g, '');

    if (pageId.length === 32) {
        try {
            const response = await fetch('http://localhost:8080/api/quiz/generate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pageId })
            });

            const raw = await response.text();

            if (!response.ok) {
                let errorText = raw;
                try {
                    const parsed = JSON.parse(raw);
                    errorText = parsed.message || parsed.error || raw;
                } catch (_) {
                    // keep raw text when body is not JSON
                }
                status.innerText = `실패(${response.status}): ${errorText}`;
                return;
            }

            status.innerText = "성공: " + raw;
        } catch (error) {
            status.innerText = "에러: 서버가 꺼져있거나 CORS/네트워크 문제가 있습니다.";
        }
    } else {
        status.innerText = "노션 페이지가 아닌 것 같습니다.";
    }
});
