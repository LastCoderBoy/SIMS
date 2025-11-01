const urlParams = new URLSearchParams(window.location.search);
const token = urlParams.get('token');
fetch(`/api/v1/email/confirm-status?token=${token}`)
    .then(response => response.json())
    .then(data => {
        const alertDiv = document.getElementById('alert');
        alertDiv.className = data.alertClass;
        alertDiv.innerHTML = `<h2>SIMS Inventory System</h2><p>${data.message}</p><p>You can close this window.</p>`;
    })
    .catch(error => console.error('Error:', error));