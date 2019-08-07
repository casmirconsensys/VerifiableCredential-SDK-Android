/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/

import base64url from 'base64url';
import { ICommitProtectedHeaders } from '@decentralized-identity/hub-common-js';
import { alter } from './TestUtils';
import CommitStrategyBasic from '../../src/hubSession/CommitStrategyBasic';
import ProtectedCommit from '../../src/hubSession/ProtectedCommit';
import JoseToken from '../../src/crypto/protocols/jose/JoseToken';
import ProtocolTest from '../crypto/protocols/jose/ProtocolTest';
import JoseConstants from '../../src/crypto/protocols/jose/JoseConstants';
import { ProtectionFormat } from '../../src/crypto/keyStore/ProtectionFormat';
import IPayloadProtectionOptions from '../../src/crypto/protocols/IPayloadProtectionOptions';

const commitHeaders: Partial<ICommitProtectedHeaders> = {
  interface: 'Collections',
  context: 'schema.org',
  type: 'MusicPlaylist',
  operation: 'update',
  commit_strategy: 'basic',
  sub: 'did:example:sub.id',
  kid: 'did:example:iss.id#key-1',
  iss: 'did:example:iss.id',
  // object_id and committed_at intentionally omitted
};

let strategy: CommitStrategyBasic;

/**
 * Helper to build a mock signed commit using the specified headers and a default payload.
 *
 * @param headers Headers to be merged with the default headers.
 */
const buildSignedCommit = (headers: Partial<ICommitProtectedHeaders>, payload?: any) => {
const finalHeaders = alter(commitHeaders, headers);
const finalPayload = payload || { title: 'My Playlist' };

const token = new JoseToken(<IPayloadProtectionOptions> {}, new ProtocolTest(), [
    [JoseConstants.tokenFormat, ProtectionFormat.JwsFlatJson],
    [JoseConstants.tokenPayload, base64url(JSON.stringify(finalPayload))],
    [JoseConstants.tokenProtected, base64url(JSON.stringify(finalHeaders))],
    [JoseConstants.tokenSignatures, ['abcdef']]]);

  return new ProtectedCommit(token);
};

/**
 * Helper to expect that one commit precedes another.
 * 
 * @param a The commit expected to come first.
 * @param b The commit expected to come second.
 */
const expectBefore = async (a: ProtectedCommit, b: ProtectedCommit) => {
  expect(strategy['compareCommits'](a, b)).toEqual(-1);
  expect(strategy['compareCommits'](b, a)).toEqual(1);
};

describe('CommitStrategyBasic', () => {

  beforeEach(async () => {
    strategy = new CommitStrategyBasic();
  });

  describe('compareCommits()', () => {

    it('should throw if given commits from different objects', async () => {
      try {
        await expectBefore(
          buildSignedCommit({ object_id: '123' }),
          buildSignedCommit({ object_id: '456' })
        );
        fail('Not expected to reach this point.')
      } catch (err) {
        // Expected
      }
    });

    it('should respect create, update, delete ordering', async () => {
      const create = buildSignedCommit({ operation: 'create' });
      const update = buildSignedCommit({ operation: 'update', object_id: create.getObjectId() });
      const del = buildSignedCommit({ operation: 'delete', object_id: create.getObjectId() });

      await expectBefore(create, update);
      await expectBefore(update, del);
      await expectBefore(create, del);
    });

    it('should respect date ordering', async () => {
      const a = buildSignedCommit({ committed_at: '1995-12-17T03:24:00' });
      const b = buildSignedCommit({ committed_at: '1995-12-17T03:25:00' });

      await expectBefore(a, b);
    });

    it('should respect revision ordering', async () => {
      for (let i = 0; i < 100; i++) {
        const a = buildSignedCommit({
          committed_at: '1995-12-17T03:24:00',
          iss: Math.random().toString()
        });
        const b = buildSignedCommit({
          committed_at: '1995-12-17T03:24:00',
          iss: Math.random().toString()
        });

        (a.getRevision() < b.getRevision())
          ? await expectBefore(a, b)
          : await expectBefore(b, a);
      }
    });

    describe('resoloveObject()', () => {

      it('should return null for an empty commit list', async () => {
        expect(await strategy.resolveObject(<any> undefined)).toBeNull();
        expect(await strategy.resolveObject([])).toBeNull();
      });
  
      it('should respect create, update, delete ordering', async () => {
        const create = buildSignedCommit({ operation: 'create' }, { op: 'create' });
        const update = buildSignedCommit({ operation: 'update', object_id: create.getObjectId() }, { op: 'update' });

        expect(await strategy.resolveObject([update, create])).toEqual({ op: 'update' });
        expect(await strategy.resolveObject([create, update])).toEqual({ op: 'update' });
      });

      it('should return null for a deleted object', async () => {
        const createCommit = buildSignedCommit({ operation: 'create' }, { op: 'create' });
        const deleteCommit = buildSignedCommit({ operation: 'delete', object_id: createCommit.getObjectId() }, {});

        expect(await strategy.resolveObject([createCommit, deleteCommit])).toBeNull();
        expect(await strategy.resolveObject([deleteCommit, createCommit])).toBeNull();
      });

    });

  });
});
