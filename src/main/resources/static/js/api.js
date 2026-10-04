// Camada de acesso à API REST. Todo o resto do front-end só fala com a API por aqui.

async function request(path, { method = 'GET', body } = {}) {
    const response = await fetch(`/api${path}`, {
        method,
        headers: body ? { 'Content-Type': 'application/json' } : {},
        body: body ? JSON.stringify(body) : undefined,
    });
    if (response.status === 204) return null;

    const data = await response.json().catch(() => null);
    if (!response.ok) {
        const error = new Error(data?.mensagem || 'Algo deu errado. Tente novamente.');
        error.campos = data?.campos ?? {};
        throw error;
    }
    return data;
}

export const api = {
    listarCasas: () => request('/casas'),
    criarCasa: (nome) => request('/casas', { method: 'POST', body: { nome } }),
    buscarCasa: (id) => request(`/casas/${id}`),
    resumo: (casaId) => request(`/casas/${casaId}/resumo`),
    renomearCasa: (id, nome) => request(`/casas/${id}`, { method: 'PUT', body: { nome } }),

    adicionarMorador: (casaId, nome) =>
        request(`/casas/${casaId}/moradores`, { method: 'POST', body: { nome } }),
    removerMorador: (casaId, moradorId) =>
        request(`/casas/${casaId}/moradores/${moradorId}`, { method: 'DELETE' }),

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
