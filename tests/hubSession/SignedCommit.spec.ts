/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/

import { ICommitProtectedHeaders, IFlattenedJws } from '@decentralized-identity/hub-common-js';
import base64url from 'base64url';
import SignedCommit from '../../src/hubSession/SignedCommit';

const createHeaders: ICommitProtectedHeaders = {
  interface: 'Collections',
  context: 'schema.org',
  type: 'MusicPlaylist',
  operation: 'create',
  committed_at: '2019-01-01',
  commit_strategy: 'basic',
  sub: 'did:example:sub.id',
  kid: 'did:example:client.id#key-1',
  iss: 'did:example:client.id',
};

describe('SignedCommit', () => {

  describe('getProtectedHeaders()', () => {

    it('should return the headers', async () => {
      const signedCommit = new SignedCommit({
        protected: base64url(JSON.stringify(createHeaders)),
        payload: base64url(JSON.stringify({ name: 'test' })),
        signature: 'abc',
      });

      expect(signedCommit.getProtectedHeaders()).toEqual(createHeaders);
    });

    it('should throw if protected headers are missing', async () => {
      const signedCommit = new SignedCommit(<IFlattenedJws> {
        payload: base64url(JSON.stringify({ name: 'test' })),
        signature: 'abc',
      });

      try {
        signedCommit.getProtectedHeaders();
        fail('Should not reach this point.')
      } catch (e) {
        // Expected
      }
    });

  });

  describe('getPayload()', () => {

    it('should return a json payload', async () => {
      const payload = {
        name: 'test'
      };

      const signedCommit = new SignedCommit({
        protected: base64url(JSON.stringify(createHeaders)),
        payload: base64url(JSON.stringify(payload)),
        signature: 'abc',
      });

      expect(signedCommit.getPayload()).toEqual(payload);
    });

    it('should return a non-json payload', async () => {
      const payload = 'test';

      const signedCommit = new SignedCommit({
        protected: base64url(JSON.stringify(createHeaders)),
        payload: base64url(payload),
        signature: 'abc',
      });

      expect(signedCommit.getPayload()).toEqual(payload);
    });

    it('should throw if a payload is missing', async () => {
      const signedCommit = new SignedCommit(<IFlattenedJws> {
        protected: base64url(JSON.stringify(createHeaders)),
        signature: 'abc',
      });

      try {
        signedCommit.getPayload();
        fail('Should not reach this point.')
      } catch (e) {
        // Expected
      }
    });

  });

  describe('getObjectId()', () => {

    it('should return the revision for a create commit', async () => {
      
      const signedCommit = new SignedCommit({
        protected: base64url(JSON.stringify(createHeaders)),
        payload: base64url(JSON.stringify({ name: 'test '})),
        signature: 'abc',
      });

      expect(signedCommit.getObjectId()).toEqual(signedCommit.getRevision());
    });

    it('should return the revision for an update commit', async () => {
      
      const updateHeaders = Object.assign({}, createHeaders, {
        operation: 'update',
        object_id: 'abc123'
      });

      const signedCommit = new SignedCommit({
        protected: base64url(JSON.stringify(updateHeaders)),
        payload: base64url(JSON.stringify({ name: 'test '})),
        signature: 'abc',
      });

      expect(signedCommit.getObjectId()).toEqual('abc123');
    });

  });

});
