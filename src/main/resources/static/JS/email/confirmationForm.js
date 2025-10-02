const urlParams = new URLSearchParams(window.location.search);
const token = urlParams.get('token');
fetch(`/api/v1/SIMS/confirm-form?token=${token}`)
    .then(response => response.json())
    .then(data => {
        if (!data.valid) {
            window.location.href = '../../HTML/email/confirmation.html?token=' + token;
        }
        document.getElementById('poNumber').innerText = 'Confirm Purchase Order: ' + data.poNumber;
    });

document.getElementById('confirmForm').addEventListener('submit', function(e) {
    e.preventDefault();
    const date = document.getElementById('expectedArrivalDate').value;
    fetch(`/api/v1/SIMS/confirm?token=${token}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ expectedArrivalDate: date })
    })
        .then(response => response.json())
        .then(data => {
            window.location.href = '../../HTML/email/confirmation.html?token=' + token;
        });
});