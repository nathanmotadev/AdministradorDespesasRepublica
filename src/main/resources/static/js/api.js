// Camada de acesso à API REST. Todo o resto do front-end só fala com a API por aqui.
// O token de login (JWT) fica guardado no navegador e vai em todas as requisições.

const CHAVE_TOKEN = 'republica.token';

export const sessao = {
    token: () => {
        try { return localStorage.getItem(CHAVE_TOKEN); } catch { return null; }
    },
    salvar: (token) => {
        try { localStorage.setItem(CHAVE_TOKEN, token); } catch { /* sem armazenamento: vale só nesta aba */ }
    },
    sair: () => {
        try { localStorage.removeItem(CHAVE_TOKEN); } catch { /* nada a fazer */ }
    },
};

let aoExpirar = () => {};
/** O app registra aqui o que fazer quando o servidor disser "não está logado" (HTTP 401). */
export const definirAoExpirar = (funcao) => { aoExpirar = funcao; };

async function request(path, { method = 'GET', body, publico = false } = {}) {
    const headers = {};
    if (body) headers['Content-Type'] = 'application/json';
    const token = sessao.token();
    if (token && !publico) headers.Authorization = `Bearer ${token}`;

    const response = await fetch(`/api${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });

    if (response.status === 401 && !publico) {
        sessao.sair();
        aoExpirar();
        throw new Error('Sua sessão expirou. Entre de novo.');
    }
    if (response.status === 204) return null;

    const data = await response.json().catch(() => null);
    if (!response.ok) {
        const error = new Error(data?.mensagem || 'Algo deu errado. Tente novamente.');
        error.campos = data?.campos ?? {};
        error.status = response.status;
        throw error;
    }
    return data;
}

export const api = {
    // conta
    login: (email, senha) => request('/auth/login', { method: 'POST', body: { email, senha }, publico: true }),
    cadastrarCasa: (dados) => request('/auth/cadastro/casa', { method: 'POST', body: dados, publico: true }),
    cadastrarConvite: (dados) => request('/auth/cadastro/convite', { method: 'POST', body: dados, publico: true }),

    // minha visão
    eu: () => request('/eu'),
    meuResumo: () => request('/eu/resumo'),
    minhasDividas: () => request('/eu/dividas'),

    // casa
    convite: (casaId) => request(`/casas/${casaId}/convite`),
    renomearCasa: (id, nome) => request(`/casas/${id}`, { method: 'PUT', body: { nome } }),
    adicionarMorador: (casaId, nome) =>
        request(`/casas/${casaId}/moradores`, { method: 'POST', body: { nome } }),
    removerMorador: (casaId, moradorId) =>
        request(`/casas/${casaId}/moradores/${moradorId}`, { method: 'DELETE' }),

    // despesas e cobranças
    listarDespesas: (casaId, mes) =>
        request(`/casas/${casaId}/despesas${mes ? `?mes=${mes}` : ''}`),
    criarDespesa: (casaId, despesa) =>
        request(`/casas/${casaId}/despesas`, { method: 'POST', body: despesa }),
    excluirDespesa: (casaId, despesaId) =>
        request(`/casas/${casaId}/despesas/${despesaId}`, { method: 'DELETE' }),
    cobrarTodos: (casaId, despesaId, vencimento) =>
        request(`/casas/${casaId}/despesas/${despesaId}/cobranca`, { method: 'POST', body: { vencimento } }),
    cobrar: (divisaoId, vencimento) =>
        request(`/divisoes/${divisaoId}/cobranca`, { method: 'POST', body: { vencimento } }),
    registrarPagamento: (divisaoId) =>
        request(`/divisoes/${divisaoId}/pagamento`, { method: 'POST' }),
};
