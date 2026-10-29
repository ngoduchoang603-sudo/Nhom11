/* ============================================================
   PhoneZone - Shared API client
   ============================================================ */

const API = {
    user:    'http://localhost:8081',
    product: 'http://localhost:8082',
    order:   'http://localhost:8083',
    payment: 'http://localhost:8084',
    ai:      'http://localhost:8085'
};

// Tang so nay khi backend thay doi khoa JWT / cau truc token. Nhờ vậy mot
// token cu trong trinh duyet khong tao ra loi 401 kho hieu khi mua hang.
const AUTH_VERSION = '2';

// ============ AUTH ============
const Auth = {
    get token() { return localStorage.getItem('shopToken'); },
    get user() {
        if (localStorage.getItem('shopAuthVersion') !== AUTH_VERSION) {
            this.clear();
            return null;
        }
        try { return JSON.parse(localStorage.getItem('shopUser') || 'null'); }
        catch (e) { return null; }
    },

    save(token, user) {
        localStorage.setItem('shopToken', token);
        localStorage.setItem('shopUser', JSON.stringify(user));
        localStorage.setItem('shopAuthVersion', AUTH_VERSION);
    },

    clear() {
        localStorage.removeItem('shopToken');
        localStorage.removeItem('shopUser');
        localStorage.removeItem('shopAuthVersion');
    },

    requireLogin() {
        if (!this.user) {
            window.location.href = 'login.html';
            return false;
        }
        return true;
    }
};

// ============ HTTP CLIENT ============
async function http(url, options = {}) {
    const { anonymous = false, ...requestOptions } = options;
    const headers = { 'Content-Type': 'application/json', ...(requestOptions.headers || {}) };
    if (!anonymous && Auth.token) headers.Authorization = `Bearer ${Auth.token}`;

    const r = await fetch(url, { ...requestOptions, headers });
    if (!r.ok) {
        if ((r.status === 401 || r.status === 403) && !anonymous) {
            Auth.clear();
            throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
        }
        let msg = `HTTP ${r.status}`;
        try {
            const txt = await r.text();
            if (txt) {
                try {
                    const error = JSON.parse(txt);
                    msg = error.message || error.error || msg;
                } catch (e) {
                    // Some services return plain text validation messages.
                    msg = txt;
                }
            }
        } catch (e) {}
        throw new Error(msg);
    }
    if (r.status === 204) return null;
    return r.json();
}

// ============ HELPERS ============
function formatPrice(price) {
    if (!price) return '0₫';
    return new Intl.NumberFormat('vi-VN').format(price) + 'đ';
}

// Mã hiển thị cho khách/admin. ID số vẫn chỉ là khóa nội bộ của hệ thống.
function formatOrderCode(order) {
    const date = order && order.date ? new Date(order.date) : new Date();
    const day = [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('');
    return `PZ-${day}-${String(order?.id || 0).padStart(4, '0')}`;
}

function parsePrice(str) {
    if (typeof str === 'number') return str;
    if (!str) return 0;
    return parseInt(String(str).replace(/[^\d]/g, '')) || 0;
}

// Resolve ảnh: nếu là path /img/xxx.jpg thì dùng nguyên; nếu null/empty thì placeholder
function imgUrl(image) {
    if (!image) return 'https://via.placeholder.com/200x200/f5f5f5/d70018?text=No+Image';
    // Bo "/" dau neu co, de tro toi /img/...
    if (image.startsWith('/')) return image;
    // Neu chi co ten file, them /img/
    if (!image.includes('/')) return '/img/' + image;
    return image;
}

// ============ TOAST ============
function ensureToastContainer() {
    let wrap = document.getElementById('toastWrap');
    if (!wrap) {
        wrap = document.createElement('div');
        wrap.id = 'toastWrap';
        wrap.className = 'toast-wrap';
        wrap.style.cssText = 'position:fixed;top:20px;right:20px;z-index:99999;display:flex;flex-direction:column;gap:8px';
        document.body.appendChild(wrap);
    }
    return wrap;
}

function showToast(msg, type = 'success') {
    const wrap = ensureToastContainer();
    const t = document.createElement('div');
    const colors = {
        success: '#27ae60',
        error: '#e74c3c',
        warning: '#f39c12',
        info: '#3498db'
    };
    const icons = { success: '✓', error: '✕', warning: '⚠', info: 'ℹ' };
    t.style.cssText = `
        background:${colors[type] || colors.success};
        color:#fff;padding:12px 18px;border-radius:8px;
        box-shadow:0 4px 16px rgba(0,0,0,0.15);
        font-size:14px;font-weight:500;min-width:240px;
        animation:slideInToast 0.3s ease-out;
        display:flex;align-items:center;gap:8px;
    `;
    t.innerHTML = `<span style="font-size:18px;font-weight:700">${icons[type] || '•'}</span><span>${msg}</span>`;
    wrap.appendChild(t);

    setTimeout(() => {
        t.style.transition = 'all 0.3s';
        t.style.opacity = '0';
        t.style.transform = 'translateX(20px)';
        setTimeout(() => t.remove(), 300);
    }, 3000);
}

// CSS animation for toast (inject once)
if (!document.getElementById('toastStyles')) {
    const style = document.createElement('style');
    style.id = 'toastStyles';
    style.textContent = `
        @keyframes slideInToast {
            from { transform: translateX(120%); opacity: 0; }
            to { transform: translateX(0); opacity: 1; }
        }
    `;
    document.head.appendChild(style);
}

// ============ HEADER USER AREA ============
if (!document.getElementById('headerUserStyles')) {
    const style = document.createElement('style');
    style.id = 'headerUserStyles';
    style.textContent = `
      .user-dropdown{position:relative;display:inline-flex}.user-menu-trigger{border:1.5px solid #e5e7eb;background:#fff;border-radius:10px;padding:5px 9px 5px 6px;color:#1a1a2e;display:inline-flex;align-items:center;gap:8px;cursor:pointer;font:600 13px 'Be Vietnam Pro',Arial,sans-serif;line-height:1;transition:.18s;white-space:nowrap}.user-menu-trigger:hover,.user-menu-trigger.open{border-color:#d70018;box-shadow:0 3px 10px #d7001820}.user-avatar{width:29px;height:29px;border-radius:50%;color:#fff;display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:800;flex:0 0 29px}.user-role{color:#b7791f;font-size:8px;font-weight:800;letter-spacing:.8px;text-transform:uppercase;display:block;margin-bottom:2px}.user-menu-popover{display:none;position:absolute;right:0;top:calc(100% + 8px);min-width:205px;background:#fff;border:1px solid #eee;border-radius:10px;box-shadow:0 12px 28px #00000022;padding:6px;z-index:200}.user-menu-popover.show{display:block}.user-menu-popover a{display:block;padding:10px 11px;border-radius:7px;color:#333;text-decoration:none;font-size:13px;white-space:nowrap}.user-menu-popover a:hover{background:#fff2f3;color:#d70018}.user-menu-divider{height:1px;background:#eee;margin:5px 2px}.account-caret{font-size:10px;color:#777;margin-left:1px}@media(max-width:600px){.user-menu-name{display:none}.user-menu-trigger{padding:5px}.account-caret{display:none}.user-dropdown{margin-left:auto}.cart-btn{padding:7px 9px!important;font-size:0!important}.cart-btn i{font-size:15px}.cart-count{font-size:10px!important}}
    `;
    document.head.appendChild(style);
}

function toggleUserMenu(event) {
    event.stopPropagation();
    const menu = event.currentTarget.parentElement.querySelector('.user-menu-popover');
    const trigger = event.currentTarget;
    document.querySelectorAll('.user-menu-popover.show').forEach(x => { if (x !== menu) x.classList.remove('show'); });
    document.querySelectorAll('.user-menu-trigger.open').forEach(x => { if (x !== trigger) x.classList.remove('open'); });
    menu.classList.toggle('show'); trigger.classList.toggle('open');
}
document.addEventListener('click', () => document.querySelectorAll('.user-menu-popover.show').forEach(x => x.classList.remove('show')));

function updateHeaderUserArea() {
    const userArea = document.getElementById('userArea');
    if (!userArea) return;

    const u = Auth.user;
    if (u) {
        const initial = u.username[0].toUpperCase();
        const isAdmin = u.role === 'ADMIN';
        // Mau khac biet cho admin va user
        const avatarBg = isAdmin ? '#1a1a2e' : '#d70018';
        const roleLabel = isAdmin ? '<span class="user-role">Quản trị</span>' : '';

        userArea.innerHTML = `
            <div class="user-dropdown">
                <button class="user-menu-trigger" type="button" onclick="toggleUserMenu(event)" aria-label="Mở menu tài khoản">
                    <div class="user-avatar" style="background:${avatarBg}">${initial}</div>
                    <div class="user-menu-name" style="text-align:left;line-height:1.1">
                        ${roleLabel}
                        <span style="font-size:13px;font-weight:600">${u.username}</span>
                    </div>
                    <i class="fas fa-chevron-down account-caret"></i>
                </button>
                <div class="user-menu-popover">
                    ${isAdmin ? `
                        <a href="admin/index.html" style="background:#fff8df"><i class="fas fa-cogs" style="color:#c98200"></i> <b>Trang quản trị</b></a>
                        <div class="user-menu-divider"></div>
                    ` : ''}
                    <a href="account.html"><i class="fas fa-user-circle" style="color:#d70018"></i> Tài khoản của tôi</a>
                    <a href="trade-in.html"><i class="fas fa-sync-alt" style="color:#d70018"></i> Thu cũ đổi mới</a>
                    <a href="history.html"><i class="fas fa-box-open" style="color:#d70018"></i> Đơn hàng của tôi</a>
                    <a href="cart.html"><i class="fas fa-shopping-cart" style="color:#d70018"></i> Giỏ hàng</a>
                    <div class="user-menu-divider"></div>
                    <a href="javascript:logout()"><i class="fas fa-sign-out-alt" style="color:#d70018"></i> Đăng xuất</a>
                </div>
            </div>
        `;
    } else {
        userArea.innerHTML = `
            <a href="login.html" class="header-btn-outline"><i class="fas fa-sign-in-alt"></i> Đăng nhập</a>
            <a href="register.html" class="header-btn"><i class="fas fa-user-plus"></i> Đăng ký</a>
        `;
    }
}

async function updateCartBadge() {
    const badge = document.getElementById('cartBadge');
    if (!badge || !Auth.user) {
        if (badge) badge.style.display = 'none';
        return;
    }
    try {
        const carts = await http(`${API.order}/api/carts/user/${Auth.user.id}`);
        const count = (carts || []).reduce((s, c) => s + (c.quantity || 0), 0);
        if (count > 0) {
            badge.textContent = count;
            badge.style.display = 'flex';
        } else {
            badge.style.display = 'none';
        }
    } catch (e) {
        badge.style.display = 'none';
    }
}

function logout() {
    Auth.clear();
    showToast('Đã đăng xuất', 'info');
    setTimeout(() => window.location.href = 'index.html', 600);
}

// ============ ADD TO CART ============
async function addToCart(productId, quantity = 1, variantId = null) {
    if (!Auth.user) {
        showToast('Vui lòng đăng nhập để mua hàng', 'warning');
        setTimeout(() => window.location.href = 'login.html', 800);
        return false;
    }
    try {
        const variantQuery = variantId ? `&variantId=${encodeURIComponent(variantId)}` : '';
        await http(`${API.order}/api/carts?userId=${Auth.user.id}&productId=${productId}&quantity=${quantity}${variantQuery}`, {
            method: 'POST'
        });
        rememberRecommendationSignal('add_to_cart', { productId: String(productId) });
        trackBehavior('add_to_cart', { productId });
        showToast(`Đã thêm vào giỏ hàng`, 'success');
        await updateCartBadge();
        return true;
    } catch (err) {
        const msg = err.message || '';
        if (msg.includes('409') || msg.includes('hết hàng') || msg.includes('vượt tồn kho')) {
            showToast('Sản phẩm đã hết hàng hoặc số lượng không đủ. Vui lòng thử lại sau.', 'warning');
        } else {
            showToast('Lỗi: ' + msg, 'error');
        }
        return false;
    }
}

// ============ TRANG CHỦ GỢI Ý THEO PHIÊN ============
// Chỉ lưu trong session của trình duyệt hiện tại để sắp xếp trang chủ;
// dữ liệu tự mất khi đóng trình duyệt và không gửi lên máy chủ.
const RECOMMENDATION_SESSION_KEY = 'pzRecommendationSignals';
function rememberRecommendationSignal(type, data = {}) {
    try {
        const previous = JSON.parse(sessionStorage.getItem(RECOMMENDATION_SESSION_KEY) || '[]');
        sessionStorage.setItem(RECOMMENDATION_SESSION_KEY, JSON.stringify([...previous, { type, ...data, at: Date.now() }].slice(-30)));
    } catch (_) { /* Gợi ý là tính năng phụ, không chặn luồng mua hàng. */ }
}
function getRecommendationSignals() {
    try { return JSON.parse(sessionStorage.getItem(RECOMMENDATION_SESSION_KEY) || '[]'); }
    catch (_) { return []; }
}

// ============ AI RECOMMENDATION BEHAVIOUR ============
// Chỉ ghi nhận khi khách đã đăng nhập. Dữ liệu dùng để xếp hạng gợi ý,
// không gửi token hay nội dung nhạy cảm sang AI provider.
function behaviorSessionId() {
    let id = localStorage.getItem('behaviorSessionId');
    if (!id) { id = crypto.randomUUID(); localStorage.setItem('behaviorSessionId', id); }
    return id;
}

function trackBehavior(eventType, { productId = null, productName = null, searchQuery = null } = {}) {
    // Privacy-by-default: nothing is tracked until the visitor explicitly opts in.
    if (localStorage.getItem('behaviorConsent') !== 'granted') return;
    fetch(`${API.ai}/behavior/events`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: Auth.user?.id || null, sessionId: Auth.user ? null : behaviorSessionId(), consent: true, eventType, productId, productName, searchQuery })
    }).catch(() => {});
}

async function deleteBehaviorData() {
    const userId = Auth.user?.id;
    const sessionId = userId ? null : localStorage.getItem('behaviorSessionId');
    if (!userId && !sessionId) return;
    const query = userId ? `userId=${encodeURIComponent(userId)}` : `sessionId=${encodeURIComponent(sessionId)}`;
    await fetch(`${API.ai}/behavior/data?${query}`, { method: 'DELETE' });
    localStorage.removeItem('behaviorConsent');
    localStorage.removeItem('behaviorSessionId');
    showToast('Đã xóa dữ liệu gợi ý trên thiết bị này', 'success');
}

function showBehaviorConsent() {
    // Trang chủ chỉ dùng dữ liệu của phiên duyệt hiện tại, không hiện popup.
    // Theo dõi/lưu dài hạn ở AI service vẫn không chạy nếu chưa có quyền rõ ràng.
}

// ============ COMMON HEADER COMPONENT (for non-home pages) ============
// Sử dụng nếu cần render header chung
function renderHeader() {
    return `
    <div class="top-bar">
        <div class="top-bar-inner" style="display:flex;gap:32px;width:200%">
            <span><i class="fas fa-truck"></i> Miễn phí vận chuyển toàn quốc</span>
            <span><i class="fas fa-shield-alt"></i> Bảo hành chính hãng 12 tháng</span>
            <span><i class="fas fa-phone"></i> Hotline: 1800 2097</span>
            <span><i class="fas fa-truck"></i> Miễn phí vận chuyển toàn quốc</span>
            <span><i class="fas fa-shield-alt"></i> Bảo hành chính hãng 12 tháng</span>
            <span><i class="fas fa-phone"></i> Hotline: 1800 2097</span>
        </div>
    </div>
    <header class="main-header">
        <div class="container">
            <div style="display:flex;align-items:center;gap:24px;flex-wrap:wrap">
                <a href="index.html" style="text-decoration:none;display:flex;flex-direction:column">
                    <span class="logo-text">Phone<span>Zone</span></span>
                    <span class="logo-sub">ĐIỆN THOẠI CHÍNH HÃNG</span>
                </a>
                <div class="search-bar">
                    <form onsubmit="goSearch(event)">
                        <input type="text" id="searchInput" placeholder="Bạn cần tìm gì?"/>
                        <button type="submit"><i class="fas fa-search"></i></button>
                    </form>
                </div>
                <div style="display:flex;align-items:center;gap:10px;margin-left:auto" id="userArea">
                    <!-- filled by JS -->
                </div>
                <a href="cart.html" class="cart-btn">
                    <i class="fas fa-shopping-cart"></i> Giỏ hàng
                    <span class="cart-count" id="cartBadge" style="display:none">0</span>
                </a>
            </div>
        </div>
    </header>
    `;
}

function goSearch(e) {
    e.preventDefault();
    const kw = document.getElementById('searchInput').value.trim();
    if (kw) {
        window.location.href = `search.html?q=${encodeURIComponent(kw)}`;
    } else {
        window.location.href = 'search.html';
    }
    return false;
}

// Init on every page
document.addEventListener('DOMContentLoaded', () => {
    updateHeaderUserArea();
    updateCartBadge();
    showBehaviorConsent();
});
