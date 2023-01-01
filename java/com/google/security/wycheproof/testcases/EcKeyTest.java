/**
 * @license
 * Copyright 2016 Google Inc. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// TODO(bleichen): RFC 3279 allows ECKeys with a number of different parameters.
//   E.g. public keys can specify the order, base points etc.
//   We might want to check how well these parameters are verified when parsing
//   a public key.

package com.google.security.wycheproof;

import com.google.security.wycheproof.WycheproofRunner.ExcludedTest;
import com.google.security.wycheproof.WycheproofRunner.ProviderType;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import junit.framework.TestCase;

/** EC tests */
public class EcKeyTest extends TestCase {
  /**
   * Encodings of public keys with invalid parameters. There are multiple places where a provider
   * can validate a public key: some parameters are typically validated by the KeyFactory, more
   * validation can be done by the cryptographic primitive. Unused parameters are sometimes not
   * validated at all.
   *
   * <p>This following test vectors are public key encodings with invalid parameters where we expect
   * that KeyFactory.generatePublic recognizes the problem. The documentation simply claims that an
   * InvalidKeySpecException is thrown if the given key specification is inappropriate but does not
   * specify what an appropriate key exactly is. Nonetheless we expect that the following minimal
   * validations are performed: order is a positive integer, cofactor is a small positive integer.
   * Some modifications may not be detected and must be caught by the primitives using them. E.g.,
   * it is expensive to verify the order of the group generated by the generator and hence the key
   * factory may not verify the correctness of this parameter. Thus an implementation of ECDH must
   * not trust an order claimed in the public key.
   *
   * <p>TODO(bleichen): The encoding is defined in https://tools.ietf.org/html/rfc3279 Section
   * 2.3.5. This document defines a few additional requirements and options which are not yet
   * checked: - OID for id-public-key_type must be ansi-X9.62 2 - OID for id-ecPublicKey must be
   * id-publicKeyType 1 - The intended application for the key may be indicated in the key usage
   * field (RFC 3280). - EcpkParameters can be implicitlyCA (not sure how we would specify the curve
   * in this case) - the version is always 1 - the points on the curves can be either compressed or
   * uncompressed (so far all points are uncompressed) - the seed value is optional (so far no test
   * vector specifies the seed) - the cofactor is optional but must be included for ECDH keys. (so
   * far all test vectors have a cofactor)
   *
   * <p>RFC 3279 also specifies curves over binary fields. Because of attacks against such curves,
   * i.e. "New algorithm for the discrete logarithm problem on elliptic curves" by I.Semaev
   * https://eprint.iacr.org/2015/310 such curves should no longer be used and hence testing them
   * has low priority.
   */
  public static final String[] EC_INVALID_PUBLIC_KEYS = {
    // order = -115792089210356248762697446949407573529996955224135760342422259061068512044369
    "308201333081ec06072a8648ce3d02013081e0020101302c06072a8648ce3d01"
        + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
        + "ffffffff30440420ffffffff00000001000000000000000000000000ffffffff"
        + "fffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
        + "b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277037d"
        + "812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33"
        + "576b315ececbb6406837bf51f50221ff00000000ffffffff0000000000000000"
        + "4319055258e8617b0c46353d039cdaaf02010103420004cdeb39edd03e2b1a11"
        + "a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c0b"
        + "49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
    // order = 0
    "308201123081cb06072a8648ce3d02013081bf020101302c06072a8648ce3d01"
        + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
        + "ffffffff30440420ffffffff00000001000000000000000000000000ffffffff"
        + "fffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
        + "b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277037d"
        + "812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33"
        + "576b315ececbb6406837bf51f5020002010103420004cdeb39edd03e2b1a11a5"
        + "e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c0b49"
        + "bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
    // cofactor = -1
    "308201333081ec06072a8648ce3d02013081e0020101302c06072a8648ce3d01"
        + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
        + "ffffffff30440420ffffffff00000001000000000000000000000000ffffffff"
        + "fffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
        + "b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277037d"
        + "812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33"
        + "576b315ececbb6406837bf51f5022100ffffffff00000000ffffffffffffffff"
        + "bce6faada7179e84f3b9cac2fc6325510201ff03420004cdeb39edd03e2b1a11"
        + "a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c0b"
        + "49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
    // cofactor = 0
    "308201323081eb06072a8648ce3d02013081df020101302c06072a8648ce3d01"
        + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
        + "ffffffff30440420ffffffff00000001000000000000000000000000ffffffff"
        + "fffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
        + "b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277037d"
        + "812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33"
        + "576b315ececbb6406837bf51f5022100ffffffff00000000ffffffffffffffff"
        + "bce6faada7179e84f3b9cac2fc632551020003420004cdeb39edd03e2b1a11a5"
        + "e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c0b49"
        + "bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
    // cofactor = 115792089210356248762697446949407573529996955224135760342422259061068512044369
    "308201553082010d06072a8648ce3d020130820100020101302c06072a8648ce"
        + "3d0101022100ffffffff00000001000000000000000000000000ffffffffffff"
        + "ffffffffffff30440420ffffffff00000001000000000000000000000000ffff"
        + "fffffffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0"
        + "cc53b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277"
        + "037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162b"
        + "ce33576b315ececbb6406837bf51f5022100ffffffff00000000ffffffffffff"
        + "ffffbce6faada7179e84f3b9cac2fc632551022100ffffffff00000000ffffff"
        + "ffffffffffbce6faada7179e84f3b9cac2fc63255103420004cdeb39edd03e2b"
        + "1a11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b842959"
        + "8c0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
  };

  @ExcludedTest(
      providers = {ProviderType.BOUNCY_CASTLE},
      comment = "KeyFactory.EC is removed")
  public void testEncodedPublicKey() throws Exception {
    KeyFactory kf = KeyFactory.getInstance("EC");
    for (String encodedHex : EC_INVALID_PUBLIC_KEYS) {
      byte[] encoded = TestUtil.hexToBytes(encodedHex);
      X509EncodedKeySpec x509keySpec = new X509EncodedKeySpec(encoded);
      try {
        ECPublicKey unused = (ECPublicKey) kf.generatePublic(x509keySpec);
        fail("Constructed invalid public key from:" + encodedHex);
      } catch (InvalidKeySpecException ex) {
        // OK, since the public keys have been modified.
        System.out.println(ex.toString());
      }
    }
  }

  @ExcludedTest(
      providers = {ProviderType.BOUNCY_CASTLE},
      comment = "KeyPairGenerator.EC is removed")
  public void testEncodedPrivateKey() throws Exception {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
    keyGen.initialize(EcUtil.getNistP256Params());
    KeyPair keyPair = keyGen.generateKeyPair();
    ECPrivateKey priv = (ECPrivateKey) keyPair.getPrivate();
    byte[] encoded = priv.getEncoded();
    System.out.println("Encoded ECPrivateKey:" + TestUtil.bytesToHex(encoded));
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(encoded);
    KeyFactory kf = KeyFactory.getInstance("EC");
    ECPrivateKey decoded = (ECPrivateKey) kf.generatePrivate(spec);
    assertEquals(priv.getS(), decoded.getS());
    assertEquals(priv.getParams().getCofactor(), decoded.getParams().getCofactor());
    assertEquals(priv.getParams().getCurve(), decoded.getParams().getCurve());
    assertEquals(priv.getParams().getGenerator(), decoded.getParams().getGenerator());
    assertEquals(priv.getParams().getOrder(), decoded.getParams().getOrder());
  }

  /**
   * Tests key generation for given parameters. The test can be skipped if the curve is not a
   * standard curve.
   */
  void testKeyGeneration(ECParameterSpec ecParams, boolean isStandard) throws Exception {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
    KeyPair keyPair;
    try {
      keyGen.initialize(ecParams);
      keyPair = keyGen.generateKeyPair();
    } catch (InvalidAlgorithmParameterException ex) {
      if (!isStandard) {
        return;
      }
      throw ex;
    }
    ECPublicKey pub = (ECPublicKey) keyPair.getPublic();
    ECPrivateKey priv = (ECPrivateKey) keyPair.getPrivate();
    EcUtil.checkPublicKey(pub);
    BigInteger s = priv.getS();
    // Check the length of s. Could fail with probability 2^{-32}.
    assertTrue(s.bitLength() >= EcUtil.fieldSizeInBits(ecParams.getCurve()) - 32);
    // TODO(bleichen): correct curve?
    // TODO(bleichen): use RandomUtil
  }

  @ExcludedTest(
      providers = {ProviderType.BOUNCY_CASTLE},
      comment = "KeyPairGenerator.EC is removed")
  public void testKeyGenerationAll() throws Exception {
    testKeyGeneration(EcUtil.getNistP224Params(), true);
    testKeyGeneration(EcUtil.getNistP256Params(), true);
    testKeyGeneration(EcUtil.getNistP384Params(), true);
    testKeyGeneration(EcUtil.getNistP521Params(), true);
    // Curves that are sometimes not supported.
    testKeyGeneration(EcUtil.getBrainpoolP256r1Params(), false);
  }

  /**
   * Checks that the default key size for ECDSA is up to date.
   * The test uses NIST SP 800-57 part1 revision 4, Table 2, page 53
   * http://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-57pt1r4.pdf
   * for the minimal key size of EC keys.
   * Nist recommends a minimal security strength of 112 bits for the time until 2030.
   * To achieve this security strength EC keys of at least 224 bits are required.
   */
  @ExcludedTest(
      providers = {ProviderType.BOUNCY_CASTLE},
      comment = "KeyPairGenerator.EC is removed")
  public void testDefaultKeyGeneration() throws Exception {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
    KeyPair keyPair = keyGen.generateKeyPair();
    ECPublicKey pub = (ECPublicKey) keyPair.getPublic();
    int keySize = EcUtil.fieldSizeInBits(pub.getParams().getCurve());
    if (keySize < 224) {
      fail("Expected a default key size of at least 224 bits. Size of generate key is " + keySize); 
    }
  }

  /**
   * Tries to generate a public key with a point at infinity. Public keys with a point at infinity
   * should be rejected to prevent subgroup confinement attacks.
   */
  public void testPublicKeyAtInfinity() throws Exception {
    ECParameterSpec ecSpec = EcUtil.getNistP256Params();
    try {
      ECPublicKeySpec pubSpec = new ECPublicKeySpec(ECPoint.POINT_INFINITY, ecSpec);
      fail(
          "Point at infinity is not a valid public key. "
              + pubSpec.getW().equals(ECPoint.POINT_INFINITY));
    } catch (java.lang.IllegalArgumentException ex) {
      // This is expected
    }
  }
}