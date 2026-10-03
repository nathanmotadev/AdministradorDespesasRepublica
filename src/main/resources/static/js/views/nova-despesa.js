import { api } from '../api.js';
import { abrirModal, h, toast } from '../ui.js';
import { avatar, centavos } from '../components.js';
import { brl, dividirEmCentavos, hojeISO, lerValor } from '../format.js';
import { moradoresAtivos } from './moradores.js';

const SUGESTOES = ['Mercado', 'Pizza', 'Conta de luz', 'Internet', 'Gás', 'Produtos de limpeza'];
const CHAVE_ULTIMO_PAGADOR = 'republica.ultimoPagador';

/** Formulário de nova despesa, com prévia ao vivo de quanto cada um paga. */
export function abrirNovaDespesa({ state, recarregar }) {
    const moradores = moradoresAtivos(state);
    const casaId = state.casa.id;

    const ultimo = Number(localStorage.getItem(CHAVE_ULTIMO_PAGADOR));
    const form = {
        descricao: '',
        valorTexto: '',
        data: hojeISO(),
        pagadorId: moradores.some((m) => m.id === ultimo) ? ultimo : moradores[0].id,
        participantes: new Set(moradores.map((m) => m.id)),
    };

    // ---------- prévia ----------
    const previa = h('div', { class: 'previa', 'aria-live': 'polite' });
    const erro = h('p', { class: 'field-error', role: 'alert' });
    const botaoSalvar = h('button', { class: 'btn primary', type: 'button', onclick: salvar }, 'Registrar despesa');

    function calcular() {
        const valor = lerValor(form.valorTexto);
        const participantes = moradores
            .filter((m) => form.participantes.has(m.id))
            .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR', { sensitivity: 'base' }));
        const pagador = moradores.find((m) => m.id === form.pagadorId);
        const algumDevedor = participantes.some((m) => m.id !== form.pagadorId);
        const valido = valor > 0 && form.descricao.trim().length > 0 && algumDevedor;

        let conteudo = 'Informe o valor e quem participa para ver a divisão.';
        if (valor > 0 && participantes.length > 0) {
            const partes = dividirEmCentavos(centavos(valor), participantes.length);
            const indicePagador = participantes.findIndex((m) => m.id === form.pagadorId);
            const aReceber = (centavos(valor) - (indicePagador >= 0 ? partes[indicePagador] : 0)) / 100;
            const igual = partes.every((p) => p === partes[0]);
            conteudo = algumDevedor
                ? [`${participantes.length} pessoas · `, h('strong', {}, brl(partes[0] / 100)),
                    igual ? ' por pessoa' : ' por pessoa (centavos arredondados)', h('br'),
                    h('strong', {}, pagador.nome), ' vai receber ', h('strong', {}, brl(aReceber))]
                : 'Marque pelo menos uma pessoa além de quem pagou.';
        }
        previa.replaceChildren(...[conteudo].flat());
        botaoSalvar.disabled = !valido;
    }

    // ---------- campos ----------
    const campoDescricao = h('input', { type: 'text', maxlength: 120, placeholder: 'Ex.: Mercado da semana',
        autocomplete: 'off', oninput: (e) => { form.descricao = e.target.value; calcular(); } });

    const sugestoes = h('div', { class: 'chips' }, SUGESTOES.map((texto) =>
        h('button', { type: 'button', class: 'chip simple', onclick: () => {
            form.descricao = texto; campoDescricao.value = texto; calcular();
        } }, texto)));

    const campoValor = h('input', { type: 'text', class: 'valor-input', inputmode: 'decimal', placeholder: '0,00',
        autocomplete: 'off', 'aria-label': 'Valor total em reais',
        oninput: (e) => { form.valorTexto = e.target.value; calcular(); } });

    const campoData = h('input', { type: 'date', value: form.data, max: hojeISO(),
        oninput: (e) => { form.data = e.target.value || hojeISO(); } });

    const pagadores = h('div', { class: 'chips', role: 'radiogroup', 'aria-label': 'Quem pagou' }, moradores.map((m) =>
        h('label', { class: 'chip' },
            h('input', { type: 'radio', name: 'pagador', checked: m.id === form.pagadorId,
                onchange: () => { form.pagadorId = m.id; calcular(); } }),
            avatar(m, true), m.nome)));

    const caixas = new Map();
    const participantes = h('div', { class: 'chips' }, moradores.map((m) => {
        const caixa = h('input', { type: 'checkbox', checked: true,
            onchange: (e) => {
                if (e.target.checked) form.participantes.add(m.id); else form.participantes.delete(m.id);
                calcular();
            } });
        caixas.set(m.id, caixa);
        return h('label', { class: 'chip' }, caixa, avatar(m, true), m.nome);
    }));

    const marcarTodos = (marcado) => {
        form.participantes = new Set(marcado ? moradores.map((m) => m.id) : []);
        caixas.forEach((caixa) => { caixa.checked = marcado; });
        calcular();
    };

    // ---------- envio ----------
    async function salvar() {
        erro.textContent = '';
        botaoSalvar.disabled = true;
        try {
            await api.criarDespesa(casaId, {
                descricao: form.descricao.trim(),
                valorTotal: lerValor(form.valorTexto),
                data: form.data,
                pagadorId: form.pagadorId,
                participantesIds: [...form.participantes],
            });
            localStorage.setItem(CHAVE_ULTIMO_PAGADOR, String(form.pagadorId));
            fechar();
            toast('Despesa registrada e dividida');
            await recarregar();
        } catch (e) {
            const detalhes = Object.values(e.campos ?? {});
            erro.textContent = detalhes.length ? detalhes.join(' · ') : e.message;
            botaoSalvar.disabled = false;
        }
    }

    const corpo = h('form', { onsubmit: (e) => { e.preventDefault(); if (!botaoSalvar.disabled) salvar(); } },
        h('div', { class: 'field' }, h('label', {}, 'Valor total (R$)'), campoValor),
        h('div', { class: 'field' }, h('label', {}, 'O que foi?'), campoDescricao, sugestoes),
        h('div', { class: 'field' }, h('span', { class: 'label' }, 'Quem pagou?'), pagadores),
        h('div', { class: 'field' },
            h('div', { class: 'card-head', style: 'margin:0' },
                h('span', { class: 'label' }, 'Quem participa?'),
                h('span', {},
                    h('button', { type: 'button', class: 'btn small ghost', onclick: () => marcarTodos(true) }, 'Todos'),
                    h('button', { type: 'button', class: 'btn small ghost', onclick: () => marcarTodos(false) }, 'Limpar'))),
            participantes),
        h('div', { class: 'field' }, h('label', {}, 'Data'), campoData),
        previa, erro,
        h('button', { type: 'submit', hidden: true }));

    calcular();
    const fechar = abrirModal({
        titulo: 'Nova despesa',
        corpo,
        rodape: [h('button', { class: 'btn', type: 'button', onclick: () => fechar() }, 'Cancelar'), botaoSalvar],
    });
}
