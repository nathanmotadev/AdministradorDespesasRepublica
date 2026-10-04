import { api } from '../api.js';
import { h } from '../ui.js';
import { logo } from '../components.js';

const CAMPO = {
    email: { name: 'email', label: 'E-mail', type: 'email', placeholder: 'voce@email.com', autocomplete: 'email' },
    senha: { name: 'senha', label: 'Senha', type: 'password', autocomplete: 'current-password' },
    novaSenha: { name: 'senha', label: 'Crie uma senha', type: 'password', placeholder: 'Mínimo de 8 caracteres',
        autocomplete: 'new-password' },
    nome: { name: 'nome', label: 'Seu nome na casa', type: 'text', placeholder: 'Como os outros te chamam',
        autocomplete: 'name', maxlength: 80 },
};

/** As três telas de entrada. Cada modo diz o que pedir e qual chamada da API fazer. */
const MODOS = {
    entrar: {
        titulo: 'Bem-vindo de volta',
        texto: 'Entre para ver o que você deve e o que tem a receber na sua casa.',
        campos: [CAMPO.email, CAMPO.senha],
        botao: 'Entrar',
        enviar: (v) => api.login(v.email, v.senha),
    },
    convite: {
        titulo: 'Entre na sua casa',
        texto: 'Use o código que o morador da sua casa te enviou. Você só vai ver as dívidas em que participa.',
        campos: [
            { name: 'codigoConvite', label: 'Código de convite', type: 'text', placeholder: 'CASA-XXXXXX',
                autocomplete: 'off', classe: 'codigo-input', maxlength: 20 },
            CAMPO.nome, CAMPO.email, CAMPO.novaSenha],
        botao: 'Criar conta e entrar',
        enviar: (v) => api.cadastrarConvite(v),
    },
    casa: {
        titulo: 'Abra a sua casa',
        texto: 'Você vira o administrador e convida quem mora com você por um código.',
        campos: [
            { name: 'nomeCasa', label: 'Nome da casa', type: 'text', placeholder: 'Ex.: Casa da Vila',
                autocomplete: 'off', maxlength: 80 },
            CAMPO.nome, CAMPO.email, CAMPO.novaSenha],
        botao: 'Criar casa e entrar',
        enviar: (v) => api.cadastrarCasa(v),
    },
};

export function renderAuth({ topbar, app, nav, aoEntrar }) {
    topbar.replaceChildren();
    nav.replaceChildren();
    app.className = 'container';
    desenhar('entrar');

    function desenhar(modo) {
        app.replaceChildren(tela(modo));
        app.querySelector('input')?.focus();
    }

    function tela(modo) {
        const config = MODOS[modo];
        const erro = h('p', { class: 'field-error', role: 'alert' });
        const botao = h('button', { class: 'btn primary block', type: 'submit' }, config.botao);

        const entradas = config.campos.map((campo) => h('input', {
            type: campo.type, name: campo.name, id: `campo-${campo.name}`, placeholder: campo.placeholder,
            autocomplete: campo.autocomplete, maxlength: campo.maxlength, class: campo.classe, required: true,
        }));

        async function enviar(evento) {
            evento.preventDefault();
            erro.textContent = '';
            botao.disabled = true;
            const valores = Object.fromEntries(entradas.map((e) => [e.name, e.value.trim()]));
            // a senha vai exatamente como foi digitada (sem "aparar" espaços)
            const campoSenha = entradas.find((e) => e.name === 'senha');
            if (campoSenha) valores.senha = campoSenha.value;
            try {
                const resposta = await config.enviar(valores);
                await aoEntrar(resposta);
            } catch (e) {
                const detalhes = Object.values(e.campos ?? {});
                erro.textContent = detalhes.length ? detalhes.join(' · ') : e.message;
                botao.disabled = false;
            }
        }

        return h('div', { class: 'auth' },
            logo(),
            h('div', {}, h('h1', {}, config.titulo), h('p', { class: 'lead' }, config.texto)),
            h('form', { onsubmit: enviar },
                config.campos.map((campo, i) => h('div', { class: 'field' },
                    h('label', { for: `campo-${campo.name}` }, campo.label), entradas[i])),
                erro, botao),
            h('div', { class: 'troca' }, ...trocas(modo)),
            h('p', { class: 'auth-slogan' }, 'Contas da casa, sem climão.'));
    }

    function trocas(modo) {
        const botao = (rotulo, destino, classe = 'btn') =>
            h('button', { class: classe, type: 'button', onclick: () => desenhar(destino) }, rotulo);
        if (modo === 'entrar') {
            return [botao('Tenho um código de convite', 'convite'), botao('Abrir uma casa nova', 'casa', 'btn ghost')];
        }
        return [botao('Já tenho conta', 'entrar', 'btn ghost')];
    }
}
