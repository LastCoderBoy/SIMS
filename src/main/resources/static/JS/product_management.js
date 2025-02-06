
const token = localStorage.getItem('token');
const userRole = localStorage.getItem('role');

if (!token) {
    window.location.href = '../html/login.html';
}


function hasAdminPrivileges(role) {
    return role === 'ROLE_ADMIN' || role === 'ROLE_MANAGER';
}


async function loadProducts() {
    try {
        const response = await fetch('/api/v1/products', {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        if (response.ok) {
            const data = await response.json();
            const container = document.getElementById('productsContainer');
            const addButton = document.getElementById('addButton');

            // Show/hide add button based on admin/manager role
            if (hasAdminPrivileges(userRole)) {
                addButton.classList.remove('hidden');
            }

            // Display products
            container.innerHTML = data.products.map(product => `
                <div class="product-card">
                    <h3>${product.name}</h3>
                    <p>Category: ${product.category}</p>
                    <p>Stock: ${product.stock}</p>
                    <p>Price: $${product.price}</p>
                    <p>Status: ${product.status}</p>
                    ${hasAdminPrivileges(userRole) ? `
                        <div class="admin-controls">
                            <button onclick="editProduct('${product.productID}')">Edit</button>
                            <button onclick="deleteProduct('${product.productID}')">Delete</button>
                        </div>
                    ` : ''}
                </div>
            `).join('');

            // Add welcome message with role
            const welcomeMessage = document.createElement('div');
            welcomeMessage.className = 'welcome-message';
            welcomeMessage.textContent = `Welcome, ${userRole.replace('ROLE_', '')}`;
            document.body.insertBefore(welcomeMessage, container);
        }
    } catch (error) {
        console.error('Error loading products:', error);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    loadProducts();
});