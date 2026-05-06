# Frontend - Automação Residencial

Interface moderna desenvolvida com Next.js para controle de dispositivos IoT.

## 🛠 Tecnologias

- **Next.js 16** - Framework React com App Router
- **React 19** - Biblioteca UI
- **TypeScript** - Tipagem estática
- **Tailwind CSS 4** - Framework CSS utility-first
- **shadcn/ui** - Componentes UI baseados em Radix UI
- **Sonner** - Notificações toast
- **Lucide React** - Ícones

## 🚀 Instalação

1. **Instale as dependências**
   ```bash
   npm install
   ```

2. **Configure as variáveis de ambiente**
   ```bash
   cp .env.local.example .env.local
   ```
   
   Edite `.env.local`:
   ```env
   NEXT_PUBLIC_API_URL=http://localhost:8080
   ```

3. **Inicie o servidor de desenvolvimento**
   ```bash
   npm run dev
   ```

4. **Acesse a aplicação**
   ```
   http://localhost:3000
   ```

## 📁 Estrutura

```
frontend/
├── src/
│   ├── app/              # App Router do Next.js
│   │   ├── dashboard/    # Página do dashboard
│   │   ├── login/        # Página de login
│   │   └── layout.tsx    # Layout principal
│   ├── components/       # Componentes React
│   │   └── ui/          # Componentes UI (shadcn)
│   ├── services/        # Serviços e APIs
│   └── lib/             # Utilitários
└── package.json
```

## 🔌 API

O frontend se comunica com o backend Spring Boot através de:

- `/api/ac` - Controle do ar condicionado
- `/api/lights/:deviceName` - Controle das lâmpadas

## 🎨 Design System

O projeto utiliza o mesmo design system do Adora Admin:
- Cores primárias: Azul (#004a98) e Accent (#00aeef)
- Suporte a dark mode
- Componentes acessíveis (Radix UI)
- Animações suaves
