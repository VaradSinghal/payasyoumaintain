FROM nginx:alpine
COPY gateway/snippets/ /etc/nginx/snippets/
COPY gateway/nginx.conf /etc/nginx/nginx.conf
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
