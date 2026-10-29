/* ============================================================
   Home page - Render sản phẩm từ API
   ============================================================ */

// Render 1 product card (match CSS class trong home.html)
function productCardHTML(p) {
    const oldPrice = p.price || 0;
    const finalPrice = Math.max(0, oldPrice - (p.discount || 0));

    const specs = [];
    if (p.ram) specs.push(`<span class="spec-tag">${p.ram}</span>`);
    if (p.storage) specs.push(`<span class="spec-tag">${p.storage}</span>`);
    if (p.screen) specs.push(`<span class="spec-tag">${p.screen}</span>`);

    const discountPercent = p.discount && oldPrice ? Math.round((p.discount / oldPrice) * 100) : 0;

    return `
        <div class="product-card">
            ${discountPercent > 0 ? `<div class="badge-discount">Giảm ${discountPercent}%</div>` : ''}
            <div class="img-wrap">
                <img src="${imgUrl(p.image)}" alt="${p.name}"
                     onerror="this.src='https://via.placeholder.com/150x150?text=No+Image'"/>
            </div>
            <div class="card-body">
                <div class="card-name">${p.name}</div>
                <div class="card-specs">${specs.join('')}</div>
                <div class="card-price">${formatPrice(finalPrice)}</div>
                ${discountPercent > 0 ? `<div style="font-size:11px;color:#999;text-decoration:line-through;margin-top:-5px;margin-bottom:7px">${formatPrice(oldPrice)}</div>` : ''}
                <div class="card-btns">
                    <a href="product-detail.html?id=${p.id}" class="btn-detail">Chi tiết</a>
                    <button data-id="${p.id}" class="btn-cart btn-add-cart"
                            onclick="addToCartFromBtn(this)">
                        <i class="fas fa-cart-plus"></i>
                    </button>
                </div>
            </div>
        </div>
    `;
}

// Wrap chung addToCart cho button (lay productId tu data-id)
async function addToCartFromBtn(btn) {
    const productId = btn.getAttribute('data-id');
    btn.disabled = true;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
    await addToCart(productId, 1);
    btn.disabled = false;
    btn.innerHTML = '<i class="fas fa-cart-plus"></i>';
}

// Navigate tu link san pham (giu compat voi onclick goToProductDetail)
function goToProductDetail(el) {
    const id = el.getAttribute('data-id') || el.dataset.id;
    if (id) window.location.href = `product-detail.html?id=${id}`;
}

// Filter theo brand: chuyen sang trang search
function filterBrand(brand) {
    if (!brand) {
        window.location.href = 'search.html';
    } else {
        window.location.href = `search.html?brand=${encodeURIComponent(brand)}`;
    }
}

function productSalePrice(product) {
    return Math.max(0, Number(product.price || 0) - Number(product.discount || 0));
}

function renderRecommendations(products) {
    const grid = document.getElementById('recommendationGrid');
    const title = document.getElementById('recommendationTitle');
    if (!grid) return;

    const signals = typeof getRecommendationSignals === 'function' ? getRecommendationSignals() : [];
    const viewedIds = new Set(signals.filter(s => s.type === 'product_view' || s.type === 'add_to_cart').map(s => String(s.productId || '')));
    const brands = signals.map(s => String(s.brand || '').toLowerCase()).filter(Boolean);
    const queries = signals.filter(s => s.type === 'search').map(s => String(s.searchQuery || '').toLowerCase()).filter(Boolean);
    const prices = signals.map(s => Number(s.price || 0)).filter(p => p > 0).sort((a, b) => a - b);
    const referencePrice = prices.length ? prices[Math.floor(prices.length / 2)] : 0;
    const hasPreference = viewedIds.size > 0 || brands.length > 0 || queries.length > 0;

    const ranked = products.filter(p => Number(p.stock || 0) > 0).map(product => {
        const brand = String(product.category || '').toLowerCase();
        const searchable = `${product.name || ''} ${product.category || ''} ${product.description || ''}`.toLowerCase();
        let score = Number(product.discount || 0) > 0 ? 2 : 0;
        if (viewedIds.has(String(product.id))) score += 12;
        score += brands.filter(b => b === brand).length * 5;
        score += queries.reduce((sum, query) => sum + query.split(/\s+/).filter(word => word.length > 2 && searchable.includes(word)).length * 2, 0);
        if (referencePrice) {
            const distance = Math.abs(productSalePrice(product) - referencePrice) / referencePrice;
            score += Math.max(0, 4 - distance * 4);
        }
        return { product, score };
    }).sort((a, b) => b.score - a.score || Number(b.product.discount || 0) - Number(a.product.discount || 0));

    const selected = ranked.slice(0, 5).map(item => item.product);
    if (hasPreference && selected.length) {
        title.textContent = 'Gợi ý dành cho bạn';
    } else {
        title.textContent = 'Sản phẩm nổi bật';
    }
    grid.innerHTML = selected.length ? selected.map(productCardHTML).join('') : '<div style="grid-column:1/-1;text-align:center;padding:30px;color:#999">Chưa có sản phẩm phù hợp</div>';
}

function escapeSearchHtml(value) {
    const box = document.createElement('div');
    box.textContent = value || '';
    return box.innerHTML;
}

function initHomeSearchSuggestions(products) {
    const input = document.getElementById('search');
    const box = document.getElementById('searchSuggestions');
    if (!input || !box) return;
    const hide = () => box.classList.remove('show');
    input.addEventListener('input', () => {
        const query = input.value.trim().toLowerCase();
        if (query.length < 2) return hide();
        const matches = products.filter(product => `${product.name || ''} ${product.category || ''}`.toLowerCase().includes(query)).slice(0, 5);
        const rows = matches.map(product => `<a class="search-suggestion" href="product-detail.html?id=${encodeURIComponent(product.id)}"><img src="${imgUrl(product.image)}" alt=""><span><b>${escapeSearchHtml(product.name)}</b><small>${formatPrice(productSalePrice(product))}</small></span></a>`).join('');
        box.innerHTML = rows + `<a class="search-all" href="search.html?name=${encodeURIComponent(input.value.trim())}"><i class="fas fa-search"></i> Xem tất cả kết quả cho “${escapeSearchHtml(input.value.trim())}”</a>`;
        box.classList.add('show');
    });
    input.addEventListener('focus', () => { if (input.value.trim().length >= 2) input.dispatchEvent(new Event('input')); });
    document.addEventListener('click', event => { if (!event.target.closest('.search-bar')) hide(); });
}

// Load san pham va render
async function loadHomePage() {
    const saleGrid = document.getElementById('saleGrid');
    const newestGrid = document.getElementById('newestGrid');
    const recommendationGrid = document.getElementById('recommendationGrid');

    // Loading state
    if (saleGrid) saleGrid.innerHTML = '<div style="grid-column:1/-1;text-align:center;padding:40px;color:#999">Đang tải...</div>';
    if (newestGrid) newestGrid.innerHTML = '<div style="grid-column:1/-1;text-align:center;padding:40px;color:#999">Đang tải...</div>';
    if (recommendationGrid) recommendationGrid.innerHTML = '<div style="grid-column:1/-1;text-align:center;padding:30px;color:#999">Đang tải gợi ý...</div>';

    try {
        // Lay sale + newest song song
        const [saleProducts, newestProducts, allProducts] = await Promise.all([
            // Product catalog is public. Do not attach a user's JWT because an
            // expired or foreign token must not block the public storefront.
            http(`${API.product}/api/products/sale`, { anonymous: true }).catch(() => []),
            http(`${API.product}/api/products/newest`, { anonymous: true }).catch(() => []),
            http(`${API.product}/api/products`, { anonymous: true }).catch(() => [])
        ]);

        renderRecommendations(allProducts);
        initHomeSearchSuggestions(allProducts);

        if (saleGrid) {
            if (saleProducts && saleProducts.length > 0) {
                saleGrid.innerHTML = saleProducts.map(productCardHTML).join('');
            } else {
                saleGrid.innerHTML = '<div style="grid-column:1/-1;text-align:center;padding:40px;color:#999">Chưa có sản phẩm khuyến mãi</div>';
            }
        }

        if (newestGrid) {
            if (newestProducts && newestProducts.length > 0) {
                newestGrid.innerHTML = newestProducts.map(productCardHTML).join('');
            } else {
                newestGrid.innerHTML = '<div style="grid-column:1/-1;text-align:center;padding:40px;color:#999">Chưa có sản phẩm</div>';
            }
        }
    } catch (err) {
        const msg = `<div style="grid-column:1/-1;text-align:center;padding:40px;color:#e74c3c">
            ⚠️ Không thể tải sản phẩm: ${err.message}
        </div>`;
        if (saleGrid) saleGrid.innerHTML = msg;
        if (newestGrid) newestGrid.innerHTML = msg;
    }
}

// Validate search
function checkSearch() {
    const v = document.getElementById('search')?.value?.trim();
    if (!v) {
        showToast('Vui lòng nhập từ khóa tìm kiếm', 'warning');
        return false;
    }
    if (typeof rememberRecommendationSignal === 'function') {
        rememberRecommendationSignal('search', { searchQuery: v });
    }
    return true;
}

// Countdown 12h
function startCountdown() {
    const end = new Date();
    end.setHours(end.getHours() + 12, 0, 0, 0);
    setInterval(() => {
        const diff = end - new Date();
        if (diff <= 0) return;
        const h = Math.floor(diff / 3600000);
        const m = Math.floor(diff % 3600000 / 60000);
        const s = Math.floor(diff % 60000 / 1000);
        const elH = document.getElementById('cd-h');
        const elM = document.getElementById('cd-m');
        const elS = document.getElementById('cd-s');
        if (elH) elH.textContent = String(h).padStart(2, '0');
        if (elM) elM.textContent = String(m).padStart(2, '0');
        if (elS) elS.textContent = String(s).padStart(2, '0');
    }, 1000);
}

// Contact popup
function openContact() {
    document.getElementById('contactOverlay').style.display = 'block';
    document.getElementById('contactPopup').style.display = 'block';
    document.getElementById('contactBtn').style.display = 'none';
}
function closeContact() {
    document.getElementById('contactOverlay').style.display = 'none';
    document.getElementById('contactPopup').style.display = 'none';
    document.getElementById('contactBtn').style.display = 'flex';
}

function escapeAiHtml(value) {
    const element = document.createElement('div');
    element.textContent = value || '';
    return element.innerHTML;
}

function renderAiMarkdown(value) {
    return escapeAiHtml(value)
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        .replace(/(^|\n)\s*[*-]\s+/g, '$1• ')
        .replace(/\n/g, '<br>');
}

function addAiMessage(role, text) {
    const messages = document.getElementById('aiChatMessages');
    if (!messages) return;
    messages.insertAdjacentHTML('beforeend', `<div class="ai-message ${role}">${renderAiMarkdown(text)}</div>`);
    messages.scrollTop = messages.scrollHeight;
}

function openAiChat() {
    // Close the contact sheet first so its overlay cannot hide the chat panel.
    closeContact();
    document.getElementById('aiChatPanel').style.display = 'block';
    const messages = document.getElementById('aiChatMessages');
    if (!messages.dataset.greeted) {
        addAiMessage('bot', 'Chào bạn! Tôi có thể tư vấn điện thoại theo giá, nhu cầu và tồn kho.');
        messages.dataset.greeted = 'true';
    }
    document.getElementById('aiChatInput').focus();
}

function closeAiChat() {
    document.getElementById('aiChatPanel').style.display = 'none';
    document.getElementById('contactBtn').style.display = 'flex';
}

async function sendAiMessage(event) {
    event.preventDefault();
    const input = document.getElementById('aiChatInput');
    const message = input.value.trim();
    if (!message) return;
    input.value = '';
    addAiMessage('user', message);
    const sessionId = localStorage.getItem('aiChatSession') || crypto.randomUUID();
    localStorage.setItem('aiChatSession', sessionId);
    try {
        const response = await http(`${API.ai}/chat`, {
            method: 'POST', body: JSON.stringify({ sessionId, message })
        });
        addAiMessage('bot', response.answer || 'Tôi chưa có câu trả lời phù hợp.');
    } catch (error) {
        addAiMessage('bot', 'Trợ lý đang bận. Bạn hãy thử lại sau ít phút.');
    }
}

// Scroll-to-top button
function initScrollBtn() {
    const btn = document.getElementById('scrollTopBtn');
    if (!btn) return;
    window.addEventListener('scroll', () => {
        btn.style.display = window.scrollY > 300 ? 'flex' : 'none';
    });
}

// Init when DOM ready
document.addEventListener('DOMContentLoaded', () => {
    loadHomePage();
    startCountdown();
    initScrollBtn();

    // Smooth scroll for anchor links
    document.querySelectorAll('.brand-nav a[href^="#"]').forEach(a => {
        a.addEventListener('click', e => {
            e.preventDefault();
            const t = document.querySelector(a.getAttribute('href'));
            if (t) t.scrollIntoView({ behavior: 'smooth', block: 'start' });
        });
    });
});
