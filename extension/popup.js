document.getElementById('sendBtn').addEventListener('click', async () => {
    const status = document.getElementById('status');
    status.innerText = "분석 중...";

    let [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    const url = tab.url;

    const pageId = url.split('-').pop();

    if (pageId && pageId.length === 32) {
        try {
            const response = await fetch('http://localhost:8080/api/quiz/page/new', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pageId: pageId })
            });
            const result = await response.text();
            status.innerText = "성공: " + result;
        } catch (error) {
            status.innerText = "에러: 서버가 꺼져있나요?";
        }
    } else {
        status.innerText = "노션 페이지가 아닌 것 같습니다.";
    }
});