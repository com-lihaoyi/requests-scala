Client certificate downloaded from [badssl.com/download](https://badssl.com/download/).

When badssl.com update certificate settings this needs to be re-done.

Download the file `badssl.com-client.p12` and use `openssl` with below instruction to generate the nopass version aswell.
Both are needed to run the tests without warnings.

```
openssl pkcs12 -clcerts -nokeys -in "badssl.com-client.p12" \
      -out badssl.com.crt -password pass:badssl.com -passin pass:badssl.com

openssl pkcs12 -cacerts -nokeys -in "badssl.com-client.p12" \
      -out badssl.com.ca-cert.ca -password pass:badssl.com -passin pass:badssl.com

openssl pkcs12 -nocerts -in "badssl.com-client.p12" \
      -out badssl.com.private.key -password pass:badssl.com -passin pass:badssl.com \
      -passout pass:TemporaryPassword

openssl rsa -in badssl.com.private.key -out "badssl.com.private-nopass.key" \
      -passin pass:TemporaryPassword

cat "badssl.com.private-nopass.key"  \
      "badssl.com.crt" \
      "badssl.com.ca-cert.ca" > temp.pem

openssl pkcs12 -export -nodes -CAfile ca-cert.ca \
     -in temp.pem -out "badssl.com-client-nopass.p12"
```

And then answer `<enter> <enter>` for no password.

Remove temporary files.

```rm badssl.com.ca-cert.ca badssl.com.crt badssl.com.private.key badssl.com.private-nopass.key temp.pem```