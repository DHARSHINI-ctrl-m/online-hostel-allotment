(() => {
  const API_BASE = 'http://localhost:8080/api';

  function getSession() {
    try { return JSON.parse(localStorage.getItem('session') || 'null'); } catch { return null; }
  }
  function setSession(session) {
    localStorage.setItem('session', JSON.stringify(session));
  }
  function clearSession() { localStorage.removeItem('session'); }

  async function request(path, options = {}) {
    const session = getSession();
    const headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
    if (session?.token) headers['X-Auth-Token'] = session.token;
    const res = await fetch(`${API_BASE}${path}`, Object.assign({}, options, { headers }));
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.message || 'Request failed');
    return data;
  }

  const HostelAPI = {
    getUser() { return getSession()?.user || null; },
    ensureStudent() {
      const s = getSession();
      if (!s || s.user?.role !== 'student') location.href = 'index.html';
    },
    ensureAdmin() {
      const s = getSession();
      if (!s || s.user?.role !== 'admin') location.href = 'index.html';
    },
    async login(email, password, role) {
      const res = await request('/login', { method: 'POST', body: JSON.stringify({ email, password, role }) });
      if (res.success) setSession({ token: res.token, user: res.user });
      return res;
    },
    async register(name, email, password) {
      return await request('/register', { method: 'POST', body: JSON.stringify({ name, email, password }) });
    },
    async logout() { try { await request('/logout', { method: 'POST' }); } finally { clearSession(); } },

    async listRooms() { return await request('/rooms'); },
    async bookRoom(roomId) { return await request('/book', { method: 'POST', body: JSON.stringify({ roomId }) }); },
    async getMyBooking() { return await request('/myBooking'); },
    async cancelMyBooking() { return await request('/myBooking', { method: 'DELETE' }); },

    async adminAddRoom(room) { return await request('/admin/rooms', { method: 'POST', body: JSON.stringify(room) }); },
    async adminListBookings() { return await request('/admin/bookings'); }
  };

  window.HostelAPI = HostelAPI;
})();







