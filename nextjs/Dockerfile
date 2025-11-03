FROM node:18-alpine AS base

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install

COPY . .

COPY .env.local .env.local

RUN npm run build

EXPOSE 3000
CMD ["npm", "run", "start"]