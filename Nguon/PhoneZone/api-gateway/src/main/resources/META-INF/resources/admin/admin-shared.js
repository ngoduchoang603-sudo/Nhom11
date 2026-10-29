/* ============================================================
   PhoneZone Admin - Shared utilities & sidebar layout
   ============================================================ */

// Import API config (api.js phai duoc include truoc)
// API, Auth, http, formatPrice, showToast, imgUrl da co san

// ============ AUTH CHECK ============
// Tat ca trang admin phai goi check nay
function requireAdmin() {
    const u = Auth.user;
    if (!u) {
        alert('Vui lòng đăng nhập');
        window.location.href = '../login.html';
        return false;
    }
    if (u.role !== 'ADMIN') {
        alert('Bạn không có quyền truy cập trang quản trị');
        window.location.href = '../index.html';
        return false;
    }
    return true;
}

// ============ SIDEBAR ============
function renderSidebar(activePage) {
    const u = Auth.user || { username: 'admin', role: 'ADMIN' };
    const initial = u.username[0].toUpperCase();

    const menu = [
        { id: 'dashboard', icon: 'fa-chart-pie', label: 'Dashboard', href: 'index.html' },
        { id: 'user',      icon: 'fa-users',     label: 'Người dùng', href: 'users.html' },
        { id: 'product',   icon: 'fa-mobile-alt',label: 'Sản phẩm',   href: 'products.html' },
        { id: 'inventory', icon: 'fa-boxes-stacked', label: 'Kho & phiên bản sản phẩm', href: 'management.html?mode=inventory' },
        { id: 'promotion', icon: 'fa-tags', label: 'Khuyến mãi', href: 'management.html?mode=promotions' },
        { id: 'coupon',    icon: 'fa-ticket', label: 'Mã giảm giá', href: 'coupons.html' },
        { id: 'order',     icon: 'fa-shopping-bag', label: 'Đơn hàng', href: 'orders.html' },
        { id: 'payment',   icon: 'fa-credit-card', label: 'Thanh toán', href: 'payments.html' },
        { id: 'report',    icon: 'fa-chart-line', label: 'Báo cáo doanh thu', href: 'management.html?mode=reports' },
        { id: 'warranty',  icon: 'fa-shield-heart', label: 'Đổi trả & bảo hành', href: 'after-sales.html' }
    ];

    return `
        <a href="index.html" class="sidebar-brand">
            <div>
                <div class="logo-text">Phone<span>Zone</span></div>
                <small>Admin Panel</small>
            </div>
        </a>
        <div class="nav-section">
            <div class="nav-label">Quản trị</div>
            ${menu.map(m => `
                <a class="nav-item ${m.id === activePage ? 'active' : ''}" href="${m.href}">
                    <i class="fas ${m.icon}"></i> ${m.label}
                </a>
            `).join('')}

            <div class="nav-label" style="margin-top:20px">Liên kết khác</div>
            <a class="nav-item" href="../index.html" target="_blank">
                <i class="fas fa-external-link-alt"></i> Xem trang khách hàng
            </a>
            <a class="nav-item" href="${API.user}/swagger" target="_blank">
                <i class="fas fa-code"></i> API: User
            </a>
            <a class="nav-item" href="${API.product}/swagger" target="_blank">
                <i class="fas fa-code"></i> API: Product
            </a>
            <a class="nav-item" href="${API.order}/swagger" target="_blank">
                <i class="fas fa-code"></i> API: Order
            </a>
            <a class="nav-item" href="${API.payment}/swagger" target="_blank">
                <i class="fas fa-code"></i> API: Payment
            </a>
        </div>
        <div class="sidebar-footer">
            <div class="admin-chip">
                <div class="admin-avatar">${initial}</div>
                <div>
                    <div class="admin-name">${u.username}</div>
                    <div class="admin-role">Quản trị viên</div>
                </div>
            </div>
            <a href="javascript:adminLogout()" class="logout-btn">
                <i class="fas fa-sign-out-alt"></i> Đăng xuất
            </a>
        </div>
    `;
}

function adminLogout() {
    if (!confirm('Đăng xuất khỏi trang quản trị?')) return;
    Auth.clear();
    window.location.href = '../index.html';
}

// ============ COMMON STYLES ============
function adminStyles() {
    return `
        *{box-sizing:border-box}
        body{font-family:'Be Vietnam Pro',sans-serif;background:#f5f5f5;margin:0}
        :root{--sidebar:240px;--red:#d70018;--navy:#1a1a2e}
        .sidebar{width:var(--sidebar);position:fixed;top:0;left:0;height:100vh;background:var(--navy);display:flex;flex-direction:column;z-index:1000;overflow-y:auto}
        .sidebar-brand{padding:20px 24px;display:flex;align-items:center;gap:10px;border-bottom:1px solid rgba(255,255,255,.1);text-decoration:none}
        .sidebar-brand .logo-text{font-size:20px;font-weight:800;color:#fff;letter-spacing:-0.5px}
        .sidebar-brand .logo-text span{color:var(--red)}
        .sidebar-brand small{font-size:9px;color:rgba(255,255,255,.5);letter-spacing:2px;text-transform:uppercase;display:block;margin-top:1px}
        .nav-section{padding:16px 0;flex:1}
        .nav-label{font-size:9px;font-weight:700;color:rgba(255,255,255,.35);text-transform:uppercase;letter-spacing:1.5px;padding:0 24px;margin-bottom:4px}
        .nav-item{display:flex;align-items:center;gap:10px;padding:11px 24px;color:rgba(255,255,255,.7);text-decoration:none;font-size:13px;font-weight:500;transition:.2s;border-left:3px solid transparent}
        .nav-item:hover{background:rgba(255,255,255,.07);color:#fff;text-decoration:none}
        .nav-item.active{background:rgba(215,0,24,.15);color:#fff;border-left-color:var(--red)}
        .nav-item i{width:18px;text-align:center;font-size:14px}
        .sidebar-footer{padding:16px;border-top:1px solid rgba(255,255,255,.1)}
        .admin-chip{display:flex;align-items:center;gap:8px;margin-bottom:10px}
        .admin-avatar{width:32px;height:32px;border-radius:50%;background:var(--red);display:flex;align-items:center;justify-content:center;color:#fff;font-size:12px;font-weight:700;flex-shrink:0}
        .admin-name{font-size:12px;color:rgba(255,255,255,.8);font-weight:600}
        .admin-role{font-size:10px;color:rgba(255,255,255,.4)}
        .logout-btn{display:flex;align-items:center;gap:8px;width:100%;padding:9px 12px;background:rgba(215,0,24,.2);color:#ff8080;border:none;border-radius:8px;font-size:12px;font-weight:600;font-family:'Be Vietnam Pro',sans-serif;cursor:pointer;text-decoration:none;transition:.2s}
        .logout-btn:hover{background:var(--red);color:#fff;text-decoration:none}
        .main{margin-left:var(--sidebar);padding:28px;min-height:100vh}
        .page-header{background:#fff;border-radius:10px;padding:16px 20px;margin-bottom:20px;display:flex;align-items:center;justify-content:space-between;border-left:4px solid var(--red);box-shadow:0 2px 8px rgba(0,0,0,.05)}
        .page-header h1{font-size:18px;font-weight:700;color:var(--navy);margin:0;display:flex;align-items:center;gap:8px}
        .page-header h1 i{color:var(--red)}
        .card-box{background:#fff;border-radius:10px;padding:20px;box-shadow:0 2px 8px rgba(0,0,0,.05);margin-bottom:20px}
        .btn-red{background:var(--red);color:#fff;border:none;border-radius:8px;padding:8px 18px;font-size:13px;font-weight:600;font-family:'Be Vietnam Pro',sans-serif;cursor:pointer;text-decoration:none;display:inline-flex;align-items:center;gap:6px;transition:.2s}
        .btn-red:hover{background:#b5000f;color:#fff;text-decoration:none}
        .btn-warn{background:#f39c12;color:#fff;border:none;border-radius:6px;padding:5px 12px;font-size:12px;font-weight:600;font-family:'Be Vietnam Pro',sans-serif;cursor:pointer;text-decoration:none;display:inline-flex;align-items:center;gap:4px;transition:.2s}
        .btn-warn:hover{background:#d68910;color:#fff;text-decoration:none}
        .btn-del{background:#e74c3c;color:#fff;border:none;border-radius:6px;padding:5px 12px;font-size:12px;font-weight:600;font-family:'Be Vietnam Pro',sans-serif;cursor:pointer;text-decoration:none;display:inline-flex;align-items:center;gap:4px;transition:.2s}
        .btn-del:hover{background:#c0392b;color:#fff;text-decoration:none}
        .btn-info{background:#3498db;color:#fff;border:none;border-radius:6px;padding:5px 12px;font-size:12px;font-weight:600;font-family:'Be Vietnam Pro',sans-serif;cursor:pointer;text-decoration:none;display:inline-flex;align-items:center;gap:4px;transition:.2s}
        .btn-info:hover{background:#2980b9;color:#fff;text-decoration:none}
        .btn-success{background:#27ae60;color:#fff;border:none;border-radius:6px;padding:5px 12px;font-size:12px;font-weight:600;font-family:'Be Vietnam Pro',sans-serif;cursor:pointer;text-decoration:none;display:inline-flex;align-items:center;gap:4px;transition:.2s}
        .btn-success:hover{background:#229954;color:#fff;text-decoration:none}
        .btn-outline{background:#fff;color:var(--navy);border:1.5px solid #ddd;border-radius:8px;padding:7px 16px;font-size:13px;font-weight:600;font-family:'Be Vietnam Pro',sans-serif;cursor:pointer;text-decoration:none;display:inline-flex;align-items:center;gap:6px;transition:.2s}
        .btn-outline:hover{border-color:var(--red);color:var(--red);text-decoration:none}
        table{width:100%;border-collapse:collapse;font-size:13px}
        table thead th{background:#f8f8f8;border-bottom:2px solid #eee;padding:10px 12px;font-size:11px;font-weight:700;color:#888;text-transform:uppercase;letter-spacing:.5px;text-align:left}
        table tbody td{padding:10px 12px;border-bottom:1px solid #f5f5f5;color:var(--navy);vertical-align:middle}
        table tbody tr:hover td{background:#fafafa}
        .badge{display:inline-block;padding:3px 10px;border-radius:4px;font-size:11px;font-weight:700}
        .badge-disc{background:#fff0f0;color:var(--red)}
        .badge-stock{background:#eafaf1;color:#27ae60}
        .badge-stock.low{background:#fff3cd;color:#856404}
        .badge-stock.out{background:#fadbd8;color:#922b21}
        .badge-role-admin{background:#fef3c7;color:#92400e}
        .badge-role-user{background:#dbeafe;color:#1e40af}
        .badge-status{padding:3px 10px;border-radius:12px;font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.3px}
        .badge-PENDING{background:#fff3cd;color:#856404}
        .badge-CONFIRMED{background:#d1ecf1;color:#0c5460}
        .badge-PACKING{background:#e8daef;color:#6c3483}
        .badge-READY_TO_SHIP{background:#d5f5e3;color:#1e8449}
        .badge-SHIPPING{background:#cce5ff;color:#004085}
        .badge-OUT_FOR_DELIVERY{background:#fcf3cf;color:#9a7d0a}
        .badge-DELIVERED{background:#d4edda;color:#155724}
        .badge-CANCELLED{background:#f8d7da;color:#721c24}
        .product-img{width:52px;height:52px;object-fit:contain;border-radius:6px;background:#f5f5f5;cursor:pointer;transition:.2s;padding:2px}
        .product-img:hover{transform:scale(1.1)}
        .form-label{font-size:12px;font-weight:700;color:#555;text-transform:uppercase;letter-spacing:.4px;margin-bottom:4px;display:block}
        .form-control,.form-select{border:1.5px solid #eee;border-radius:8px;padding:9px 12px;font-size:13px;font-family:'Be Vietnam Pro',sans-serif;transition:.2s;outline:none;width:100%}
        .form-control:focus,.form-select:focus{border-color:var(--red);box-shadow:none}
        .stat-card{background:#fff;border-radius:10px;padding:20px;text-align:center;box-shadow:0 2px 8px rgba(0,0,0,.05);transition:.2s}
        .stat-card:hover{transform:translateY(-3px);box-shadow:0 4px 16px rgba(0,0,0,.1)}
        .stat-icon{font-size:28px;margin-bottom:10px}
        .stat-num{font-size:26px;font-weight:800;color:var(--navy)}
        .stat-lbl{font-size:11px;color:#aaa;font-weight:600;text-transform:uppercase;letter-spacing:.5px;margin-top:4px}
        .chart-title{font-size:15px;font-weight:700;color:var(--navy);margin:0 0 16px;display:flex;gap:8px;align-items:center}.chart-title i{color:var(--red)}
        .price{color:var(--red);font-weight:700}
        .empty-state{text-align:center;padding:50px 20px;color:#999}
        .empty-state i{font-size:50px;color:#ddd;margin-bottom:14px}
        .loading{text-align:center;padding:40px;color:#999}

        /* Modal */
        .modal-bg{position:fixed;inset:0;background:rgba(0,0,0,0.5);z-index:2000;display:none;align-items:center;justify-content:center;padding:20px}
        .modal-bg.show{display:flex}
        .modal-content{background:#fff;border-radius:12px;max-width:520px;width:100%;max-height:90vh;overflow-y:auto;animation:slideUp 0.3s}
        @keyframes slideUp{from{transform:translateY(30px);opacity:0}to{transform:translateY(0);opacity:1}}
        .modal-header-x{padding:16px 20px;border-bottom:1px solid #eee;display:flex;justify-content:space-between;align-items:center;background:var(--navy);color:#fff;border-radius:12px 12px 0 0}
        .modal-header-x h3{font-size:16px;font-weight:700;margin:0}
        .modal-close-x{background:rgba(255,255,255,0.15);border:none;color:#fff;width:28px;height:28px;border-radius:50%;font-size:14px;cursor:pointer;transition:.2s}
        .modal-close-x:hover{background:rgba(255,255,255,0.25);transform:rotate(90deg)}
        .modal-body-x{padding:20px}
        .modal-footer-x{padding:14px 20px;border-top:1px solid #eee;display:flex;gap:8px;justify-content:flex-end}

        /* Layout helpers */
        .row-g{display:grid;gap:16px}
        .grid-stats{grid-template-columns:repeat(4,1fr)}
        .grid-cards{grid-template-columns:repeat(3,1fr)}
        .form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}
        .form-grid .full{grid-column:1/-1}

        @media(max-width:900px){
            .sidebar{transform:translateX(-100%);transition:transform 0.3s}
            .sidebar.show{transform:translateX(0)}
            .main{margin-left:0}
            .grid-stats{grid-template-columns:repeat(2,1fr)}
            .grid-cards{grid-template-columns:1fr}
            .form-grid{grid-template-columns:1fr}
        }
    `;
}

// Common modal helper
function showModal(html) {
    const existing = document.getElementById('adminModal');
    if (existing) existing.remove();

    const modal = document.createElement('div');
    modal.id = 'adminModal';
    modal.className = 'modal-bg show';
    modal.innerHTML = `<div class="modal-content">${html}</div>`;
    modal.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
    });
    document.body.appendChild(modal);
}

function closeModal() {
    const m = document.getElementById('adminModal');
    if (m) m.remove();
}

// XSS-safe HTML escape helper
function esc(s) { const d = document.createElement('div'); d.textContent = s ?? ''; return d.innerHTML; }

// Init layout for any admin page
function initAdminLayout(activePage) {
    if (!requireAdmin()) return false;

    // Inject styles
    if (!document.getElementById('adminStyles')) {
        const style = document.createElement('style');
        style.id = 'adminStyles';
        style.textContent = adminStyles();
        document.head.appendChild(style);
    }

    // Render sidebar
    const sb = document.getElementById('sidebar');
    if (sb) sb.innerHTML = renderSidebar(activePage);
    return true;
}
