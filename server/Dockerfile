FROM python:3.12-slim

WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY admin ./admin

ENV KIDTV_DATA_DIR=/data
VOLUME /data
EXPOSE 8600

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8600"]
