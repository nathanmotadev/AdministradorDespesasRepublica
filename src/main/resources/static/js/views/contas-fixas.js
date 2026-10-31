import { api } from '../api.js';
import { abrirModal, confirmar, h, toast } from '../ui.js';
import { avatar, executar } from '../components.js';
import { brl, dataBR, lerValor } from '../format.js';
import { moradoresAtivos } from './moradores.js';

const SUGESTOES = ['Aluguel', 'Internet', 'Luz', 'Água', 'Gás', 'Condomínio'];

/**
 * Contas que se repetem todo mês. Podem ser editadas a qualquer momento (o valor da luz muda,
 * por exemplo): a alteração vale a partir da próxima cobrança. Só quem cadastrou mexe nelas.
 */
export function cartaoContasFixas({ state, recarregar }) {
    const casaId = state.casa.id;
    const lista = h('div', { class: 'contas-fixas' }, h('p', { class: 'dica' }, 'Carregando…'));

    async function carregar() {
        try {
            const contas = await api.listarContasFixas(casaId);
            lista.replaceChildren(...(contas.length
                ? contas.map(linha)
                : [h('p', { class: 'muted' },
                    'Nenhuma conta fixa. Cadastre o aluguel, a internet… e a despesa é criada sozinha todo mês.')]));
        } catch (erro) {
            lista.replaceChildren(h('p', { class: 'field-error' }, erro.message));
        }
    }

    function linha(conta) {
        const minha = conta.pagador.id === state.eu.id;
        const quem = conta.participantes.map((p) => p.nome).join(', ');
        const detalhe = conta.ativa
            ? `Todo dia ${conta.diaDoMes} · próxima em ${dataBR(conta.proximoVencimento, false)}`
            : `Todo dia ${conta.diaDoMes} · pausada`;
        const acao = async (promessa, ok) => { if (await executar(promessa, ok)) await carregar(); };

        return h('div', { class: `conta-fixa${conta.ativa ? '' : ' pausada'}` },
            h('div', { class: 'conta-fixa-topo' },
                h('div', {},
                    h('div', { class: 'nome' }, conta.descricao),
                    h('div', { class: 'dica' }, detalhe)),
                h('strong', { class: 'num' }, brl(conta.valor))),
            h('div', { class: 'dica' }, `${minha ? 'Você paga' : `${conta.pagador.nome} paga`} · divide com ${quem}`),
            minha && h('div', { class: 'conta-fixa-acoes' },
                h('button', { class: 'btn small', type: 'button', onclick: () => abrirFormulario(conta) }, 'Editar'),
                conta.ativa
                    ? h('button', { class: 'btn small ghost', type: 'button',
                        onclick: () => acao(() => api.pausarContaFixa(casaId, conta.id), 'Conta pausada') }, 'Pausar')
                    : h('button', { class: 'btn small ghost', type: 'button',
                        onclick: () => acao(() => api.retomarContaFixa(casaId, conta.id), 'Conta retomada') }, 'Retomar'),
                h('button', { class: 'btn small ghost', type: 'button', onclick: async () => {
                    const sim = await confirmar({ titulo: 'Excluir conta fixa?', perigo: true, rotulo: 'Excluir',
                        mensagem: `"${conta.descricao}" deixa de ser criada todo mês. As despesas que já foram criadas continuam.` });
                    if (sim) await acao(() => api.excluirContaFixa(casaId, conta.id), 'Conta excluída');
                } }, 'Excluir')));
    }

    /** Formulário de criação; com {@code conta} informada, de edição. */
    function abrirFormulario(conta) {
        const moradores = moradoresAtivos(state);
        const form = {
            descricao: conta?.descricao ?? '',
            valorTexto: conta ? String(conta.valor).replace('.', ',') : '',
            dia: conta?.diaDoMes ?? new Date().getDate(),
            participantes: new Set(conta ? conta.participantes.map((p) => p.id) : moradores.map((m) => m.id)),
        };
        form.dia = Math.min(form.dia, 28);

        const erro = h('p', { class: 'field-error', role: 'alert' });
        const salvar = h('button', { class: 'btn primary', type: 'button', onclick: enviar }, conta ? 'Salvar' : 'Cadastrar');

        function validar() {
            const outros = [...form.participantes].some((id) => id !== state.eu.id);
            salvar.disabled = !(lerValor(form.valorTexto) > 0 && form.descricao.trim() && outros);
        }

        const campoDescricao = h('input', { type: 'text', maxlength: 120, value: form.descricao, placeholder: 'Ex.: Aluguel',
            autocomplete: 'off', oninput: (e) => { form.descricao = e.target.value; validar(); } });
        const sugestoes = !conta && h('div', { class: 'chips' }, SUGESTOES.map((texto) =>
            h('button', { type: 'button', class: 'chip simple', onclick: () => {
                form.descricao = texto; campoDescricao.value = texto; validar();
            } }, texto)));
        const campoValor = h('input', { type: 'text', class: 'valor-input', inputmode: 'decimal', placeholder: '0,00',
            value: form.valorTexto, autocomplete: 'off', 'aria-label': 'Valor em reais',
            oninput: (e) => { form.valorTexto = e.target.value; validar(); } });
        const campoDia = h('input', { type: 'number', min: 1, max: 28, value: form.dia, inputmode: 'numeric',
            oninput: (e) => { form.dia = Number(e.target.value); } });
        const participantes = h('div', { class: 'chips' }, moradores.map((m) =>
            h('label', { class: 'chip' },
                h('input', { type: 'checkbox', checked: form.participantes.has(m.id), onchange: (e) => {
                    if (e.target.checked) form.participantes.add(m.id); else form.participantes.delete(m.id);
                    validar();
                } }), avatar(m, true), m.nome)));

        async function enviar() {
            erro.textContent = '';
            salvar.disabled = true;
            const dados = { descricao: form.descricao.trim(), valor: lerValor(form.valorTexto),
                diaDoMes: form.dia, participantesIds: [...form.participantes] };
            try {
                if (conta) await api.editarContaFixa(casaId, conta.id, dados);
                else await api.criarContaFixa(casaId, dados);
                fechar();
                toast(conta ? 'Conta atualizada. Vale a partir da próxima cobrança' : 'Conta fixa cadastrada');
                await carregar();
                await recarregar();
            } catch (e) {
                const detalhes = Object.values(e.campos ?? {});
                erro.textContent = detalhes.length ? detalhes.join(' · ') : e.message;
                validar();
            }
        }

        const corpo = h('form', { onsubmit: (e) => { e.preventDefault(); if (!salvar.disabled) enviar(); } },
            h('p', { class: 'dica', style: 'margin-bottom:.9rem' }, conta
                ? 'Mudou o valor deste mês? É só ajustar aqui antes do vencimento.'
                : 'Você paga esta conta. Todo mês ela vira uma despesa dividida entre quem você marcar.'),
            h('div', { class: 'field' }, h('label', {}, 'Valor (R$)'), campoValor),
            h('div', { class: 'field' }, h('label', {}, 'O que é?'), campoDescricao, sugestoes),
            h('div', { class: 'field' }, h('label', {}, 'Vence todo dia (1 a 28)'), campoDia),
            h('div', { class: 'field' }, h('span', { class: 'label' }, 'Quem divide?'), participantes),
            erro, h('button', { type: 'submit', hidden: true }));

        validar();
        const fechar = abrirModal({
            titulo: conta ? 'Editar conta fixa' : 'Nova conta fixa',
            corpo,
            rodape: [h('button', { class: 'btn', type: 'button', onclick: () => fechar() }, 'Cancelar'), salvar],
        });
    }

    carregar();
    return h('section', { class: 'card', 'aria-labelledby': 'titulo-contas-fixas' },
        h('div', { class: 'card-head' },
            h('h2', { id: 'titulo-contas-fixas' }, 'Contas fixas'),
            moradoresAtivos(state).length >= 2
            && h('button', { class: 'btn small', type: 'button', onclick: () => abrirFormulario() }, '+ Nova')),
        lista);
}
