package com.liaison.commons.security.pkcs7.signandverify;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedDataParser;
import org.bouncycastle.cms.CMSSignedDataStreamGenerator;
import org.bouncycastle.cms.CMSSignerDigestMismatchException;
import org.bouncycastle.cms.CMSTypedStream;
import org.bouncycastle.cms.CMSVerifierCertificateNotValidException;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;
import org.bouncycastle.util.encoders.Base64;

import com.liaison.commons.exception.CertificateVerificationFailedException;
import com.liaison.commons.util.Base64Util;
import com.liaison.commons.util.MD5;
import com.liaison.commons.util.StreamUtil;

public class DigitalSignature {

	/**
	 * Enum categorizing the type of exception during signature validation
	 */
	public static enum ExceptionType {

		MismatchDigest, InvalidSignerCert
	};

	public static final String SIG_PROVIDER = BouncyCastleProvider.PROVIDER_NAME;

	public static final String SIG_ALGORITHM_SHA512WithRSA = "SHA512WITHRSA";
	public static final String SIG_ALGORITHM_SHA384WithRSA = "SHA384WITHRSA";
	public static final String SIG_ALGORITHM_SHA256WithRSA = "SHA256WITHRSA";

	/**
	 * Default signature algorithm
	 */
	public static final String SIG_ALGORITHM_DEFAULT = SIG_ALGORITHM_SHA512WithRSA;

	protected ValidationExceptionInformation result = null;

	/**
	 * Returns exception result when the signature is parse-able but failed signature validation.
	 *<p>
	 * If the signature is corrupted or malformed (not parse-able), this method will return a null.
	 * @return ValidationExceptionInformation
	 */
	public ValidationExceptionInformation getResult() {
		return result;
	}

	public String signBase64(InputStream rawDataStream, X509Certificate signerCert, PrivateKey signerKey) throws CertificateEncodingException,
			OperatorCreationException, CMSException, IOException {
		byte[] signature = sign(rawDataStream, signerCert, signerKey, SIG_ALGORITHM_DEFAULT);
		return Base64Util.toBase64String(signature);
	}

	/**
	 * Create and return a base64 encoded non-encapsulated signature over the rawDataStream based on the signer cert and
	 * signer key.
	 * <p>
	 * NOTE: this is an non-encapsulated signature which means the raw data is not encapsulated inside the signature.
	 *
	 * @param rawDataStream the raw data stream to be signed
	 * @param signerCert the certificate of the signer
	 * @param signerKey the private key of the signer
	 * @param signatureAlgorithm the signature algorithm, example: "SHA512WITHRSA"
	 * @return String representing the base64 encoded signature
	 * @throws CertificateEncodingException
	 * @throws OperatorCreationException
	 * @throws CMSException
	 * @throws IOException
	 */
	public String signBase64(InputStream rawDataStream, X509Certificate signerCert, PrivateKey signerKey, String signatureAlgorithm)
			throws CertificateEncodingException, OperatorCreationException, CMSException, IOException {
		byte[] signature = sign(rawDataStream, signerCert, signerKey, signatureAlgorithm);
		return Base64Util.toBase64String(signature);
	}

	/**
	 * Signs using the system default signature algorithm.
	 *
	 * @param rawDataStream the raw data stream to be signed
	 * @param signerCert the certificate of the signer
	 * @param signerKey the private key of the signer
	 * @return byte[] un-encapsulated signature
	 * @throws CertificateEncodingException
	 * @throws OperatorCreationException
	 * @throws CMSException
	 * @throws IOException
	 * @see #signData(java.io.InputStream, java.security.cert.X509Certificate, java.security.PrivateKey,
	 * java.lang.String)
	 */
	public byte[] sign(InputStream rawDataStream, X509Certificate signerCert, PrivateKey signerKey) throws CertificateEncodingException,
			OperatorCreationException, CMSException, IOException {

		return sign(rawDataStream, signerCert, signerKey, SIG_ALGORITHM_DEFAULT);
	}

	/**
	 * Create and return a non-encapsulated signature over the rawDataStream based on the signer cert and signer key.
	 * <p>
	 * NOTE: this is an non-encapsulated signature which means the raw data is not encapsulated inside the signature.
	 *
	 * @param rawDataStream the raw data stream to be signed
	 * @param signerCert the certificate of the signer
	 * @param signerKey the private key of the signer
	 * @param signatureAlgorithm the signature algorithm, example: "SHA512WITHRSA"
	 * @return signature as byte array
	 * @throws CertificateEncodingException
	 * @throws OperatorCreationException
	 * @throws CMSException
	 * @throws IOException
	 */
	public byte[] sign(InputStream rawDataStream, X509Certificate signerCert, PrivateKey signerKey, String signatureAlgorithm)
			throws CertificateEncodingException, OperatorCreationException, CMSException, IOException {

		byte signature[] = null;
		List certList = new ArrayList();

		ByteArrayOutputStream bOut = new ByteArrayOutputStream();

		certList.add(signerCert);
		Store certs = new JcaCertStore(certList);

		JcaDigestCalculatorProviderBuilder digestCalculatorProviderBuilder = new JcaDigestCalculatorProviderBuilder();
		digestCalculatorProviderBuilder.setProvider(SIG_PROVIDER);
		DigestCalculatorProvider digestCalculatorProvider = digestCalculatorProviderBuilder.build();

		JcaContentSignerBuilder contentSignerBuilder = new JcaContentSignerBuilder(signatureAlgorithm);
		contentSignerBuilder.setProvider(SIG_PROVIDER);
		ContentSigner sha1Signer = contentSignerBuilder.build(signerKey);
		JcaSignerInfoGeneratorBuilder signerInfoGeneratorBuilder = new JcaSignerInfoGeneratorBuilder(digestCalculatorProvider);
		SignerInfoGenerator signerInforGenerator = signerInfoGeneratorBuilder.build(sha1Signer, signerCert);

		CMSSignedDataStreamGenerator gen = new CMSSignedDataStreamGenerator();
		gen.addSignerInfoGenerator(signerInforGenerator);
		gen.addCertificates(certs);

		OutputStream sigOut = gen.open(bOut, false); // setting to false to exclude content of the data from the signature
		StreamUtil.streamToStream(rawDataStream, sigOut);
		sigOut.close();

		signature = bOut.toByteArray();
		checkSigParseable(signature);

		return signature;
	}

	public void verifyBase64(InputStream rawDataStream, String signatureString, List<X509Certificate> originalSignerCerts) throws OperatorCreationException,
			CMSException, IOException, CertificateException, SignatureException, CertificateVerificationFailedException {
		verify(rawDataStream, Base64.decode(signatureString), originalSignerCerts);
	}


	/**
	 * Verifies the un-encapsulated signature block against the rawDataStream.
	 * <p>
	 * This method will throw a CMSException if the signature is malformed, corrupted as well as invalid signature.  
	 * @param rawDataStream the raw data to verify against the provided signatureBlock
	 * @param signatureBlock the signatureBlock 
	 * @throws CMSException thrown if signature is invalid
	 * @throws OperatorCreationException
	 * @throws IOException
	 * @throws CertificateException 
	 * @throws CertificateVerificationFailedException 
	 * @see #getResult() 
	 */
	public void verify(InputStream rawDataStream, byte[] signatureBlock, List<X509Certificate> originalSignerCerts) throws CMSException,
			OperatorCreationException, IOException, CertificateException, CertificateVerificationFailedException {
		checkSigParseable(signatureBlock);

		JcaDigestCalculatorProviderBuilder digestCalculatorProviderBuilder = new JcaDigestCalculatorProviderBuilder();
		digestCalculatorProviderBuilder.setProvider(SIG_PROVIDER);
		DigestCalculatorProvider digestCalculatorProvider = digestCalculatorProviderBuilder.build();

		CMSSignedDataParser sp = new CMSSignedDataParser(digestCalculatorProvider, new CMSTypedStream(new BufferedInputStream(rawDataStream)), signatureBlock);

		sp.getSignedContent().drain();

		Store certStore = sp.getCertificates();
		SignerInformationStore signers = sp.getSignerInfos();

		Collection c = signers.getSigners();
		Iterator it = c.iterator();

		int certCounter = 0;

		while (it.hasNext()) {
			certCounter++;

			SignerInformation signer = (SignerInformation) it.next();
			Collection certCollection = certStore.getMatches(signer.getSID());

			Iterator certIt = certCollection.iterator();
			X509CertificateHolder cert = (X509CertificateHolder) certIt.next();

			verifyIfCertsAreSame(originalSignerCerts, cert);
			JcaSimpleSignerInfoVerifierBuilder simpleSignerInfoVerifierBuilder = new JcaSimpleSignerInfoVerifierBuilder();
			simpleSignerInfoVerifierBuilder.setProvider(SIG_PROVIDER);
			SignerInformationVerifier signerInformationVerifier = simpleSignerInfoVerifierBuilder.build(cert);

			try {
				signer.verify(signerInformationVerifier);
			}
			catch (CMSException ex) {
				result = new ValidationExceptionInformation();
				result.setCertificateHolder(cert);
				result.setSignerInformation(signer);

				if (ex instanceof CMSSignerDigestMismatchException) {
					result.setResultType(ExceptionType.MismatchDigest);
				}
				else if (ex instanceof CMSVerifierCertificateNotValidException) {
					result.setResultType(ExceptionType.InvalidSignerCert);
				}

				throw ex;
			}

		}

	}

	private void checkSigParseable(byte[] sig) throws CMSException, IOException, OperatorCreationException {

		CMSSignedDataParser sp = new CMSSignedDataParser(new JcaDigestCalculatorProviderBuilder().setProvider(SIG_PROVIDER).build(), sig);
		sp.getVersion();
		CMSTypedStream sc = sp.getSignedContent();
		if (sc != null) {
			sc.drain();
		}

		sp.getCertificates();
		sp.getCRLs();
		sp.getSignerInfos();
		sp.close();

	}

	private void verifyIfCertsAreSame(List<X509Certificate> originalSignerCerts, X509CertificateHolder certInSignature) throws CertificateEncodingException,
			CertificateVerificationFailedException, IOException {

		//Finger of certInSignature
		byte[] encodedCertInSignature = certInSignature.getEncoded();
		byte[] hashOfCertInSignature = MD5.calc(encodedCertInSignature);
		String fingerPrintOfCertInSignature = new String(Base64.encode(hashOfCertInSignature));

		for (X509Certificate originalSignerCert : originalSignerCerts) {

			byte[] encodedOriginalCert = originalSignerCert.getEncoded();
			byte[] hashOfOriginalCert = MD5.calc(encodedOriginalCert);
			String fingerPrintOfOriginalCert = new String(Base64.encode(hashOfOriginalCert));
			if (fingerPrintOfCertInSignature.equals(fingerPrintOfOriginalCert)) {
				break;
			}
			throw new CertificateVerificationFailedException("The original signer cert and the cert in the signature did not match");
		}
	}

}


/**
 * This class holds the signature validation result
 *
 */
class ValidationExceptionInformation {

	private String digestAlgorithm = null;
	private byte[] signature = null;

	private String serialNumber = null;
	private String signerSubject = null;
	private Date signerNotBefore = null;
	private Date signerNotAfter = null;
	private String issuer = null;
	private DigitalSignature.ExceptionType resultType = null;

	protected void setSignerInformation(SignerInformation si) {
		signature = si.getSignature();
		digestAlgorithm = si.getDigestAlgorithmID().toString();
	}

	protected void setCertificateHolder(X509CertificateHolder cert) {

		serialNumber = cert.getSerialNumber().toString(16);
		signerSubject = cert.getSubject().toString();
		signerNotAfter = cert.getNotAfter();
		signerNotBefore = cert.getNotBefore();
		issuer = cert.getIssuer().toString();
	}

	protected void setResultType(DigitalSignature.ExceptionType resultType) {
		this.resultType = resultType;
	}

	public String getDigestAlgorithm() {
		return digestAlgorithm;
	}

	public byte[] getSignature() {
		return signature;
	}

	public String getSerialNumber() {
		return serialNumber;
	}

	public String getSignerSubject() {
		return signerSubject;
	}

	public Date getSignerNotBefore() {
		return signerNotBefore;
	}

	public Date getSignerNotAfter() {
		return signerNotAfter;
	}

	public String getIssuer() {
		return issuer;
	}

	public DigitalSignature.ExceptionType getResultType() {
		return resultType;
	}

}