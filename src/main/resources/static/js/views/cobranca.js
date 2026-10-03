import { api } from '../api.js';
import { abrirModal, h, toast } from '../ui.js';
import { brl, dataBR, hojeISO, somarDias } from '../format.js';
import { avatar } from '../components.js';

function montarMensagem(despesa, divisao, vencimento) {
    const prazo = vencimento
        ? ` Consegue me pagar até ${dataBR(vencimento)}?`
        : ' Quando puder, me paga?';
    return `Oi, ${divisao.devedor.nome}! Passando pra lembrar da sua parte em "${despesa.descricao}": `
        + `${brl(divisao.valor)}.${prazo} Valeu! — ${despesa.pagador.nome}`;
}

/**
 * Janela de cobrança. Com uma divisão, mostra uma mensagem pronta para copiar e mandar
 * no WhatsApp; com várias ("cobrar todos"), lista quem vai ser cobrado.
 */
export function abrirCobranca({ casaId, despesa, divisoes, recarregar }) {
    const unica = divisoes.length === 1 ? divisoes[0] : null;
    let vencimento = null;

    const mensagem = unica ? h('textarea', { readOnly: true, 'aria-label': 'Mensagem de cobrança' }) : null;
    const atualizarMensagem = () => {
        if (mensagem) mensagem.value = montarMensagem(despesa, unica, vencimento);
    };

    const campoData = h('input', {
        type: 'date', min: hojeISO(), 'aria-label': 'Vencimento',
        oninput: (e) => { vencimento = e.target.value || null; atualizarMensagem(); },
    });
    const definirPrazo = (dias) => {
        vencimento = dias === null ? null : somarDias(dias);
        campoData.value = vencimento ?? '';
        atualizarMensagem();
    };
    const atalhos = [['Hoje', 0], ['Em 3 dias', 3], ['Em 7 dias', 7], ['Sem prazo', null]].map(([rotulo, dias]) =>
        h('button', { type: 'button', class: 'chip simple', onclick: () => definirPrazo(dias) }, rotulo));

    async function confirmarCobranca() {
        try {
            if (unica) await api.cobrar(unica.id, vencimento);
            else await api.cobrarTodos(casaId, despesa.id, vencimento);
            fechar();
            toast(unica ? `${unica.devedor.nome} foi cobrado(a)` : 'Cobranças registradas');
            await recarregar();
        } catch (erro) {
            toast(erro.message, 'erro');
        }
    }

    async function copiar() {
        try {
            await navigator.clipboard.writeText(mensagem.value);
            toast('Mensagem copiada');
        } catch {
            mensagem.select();
            toast('Selecione e copie a mensagem manualmente', 'erro');
        }
    }

    const corpo = h('div', {},
        unica === null && h('div', { class: 'field' },
            h('span', { class: 'label' }, 'Quem será cobrado'),
            divisoes.map((d) => h('div', { class: 'morador' }, avatar(d.devedor, true),
                h('span', { class: 'nome' }, d.devedor.nome), h('span', { class: 'num' }, brl(d.valor))))),
        h('div', { class: 'field' },
            h('span', { class: 'label' }, 'Prazo para pagar (opcional)'),
            h('div', { class: 'chips' }, atalhos), campoData),
        unica && h('div', { class: 'field' },
            h('span', { class: 'label' }, 'Mensagem pronta'), mensagem,
            h('button', { class: 'btn small', type: 'button', onclick: copiar }, 'Copiar mensagem')));

    atualizarMensagem();
    const fechar = abrirModal({
        titulo: unica ? `Cobrar ${unica.devedor.nome}` : 'Cobrar todos',
        corpo,
        rodape: [
            h('button', { class: 'btn', type: 'button', onclick: () => fechar() }, 'Cancelar'),
            h('button', { class: 'btn primary', type: 'button', onclick: confirmarCobranca }, 'Marcar como cobrada'),
        ],
    });
}
